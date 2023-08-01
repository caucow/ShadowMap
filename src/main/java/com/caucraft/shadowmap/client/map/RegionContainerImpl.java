package com.caucraft.shadowmap.client.map;

import com.caucraft.shadowmap.api.map.CeilingType;
import com.caucraft.shadowmap.api.map.MapChunk;
import com.caucraft.shadowmap.api.map.MapRegion;
import com.caucraft.shadowmap.api.map.RegionContainer;
import com.caucraft.shadowmap.api.map.RegionFlags;
import com.caucraft.shadowmap.api.storage.StorageKey;
import com.caucraft.shadowmap.api.util.ChunkCache;
import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.config.PerformanceConfig;
import com.caucraft.shadowmap.client.render.RegionRenderContextImpl;
import com.caucraft.shadowmap.client.util.MapFramebuffer;
import com.caucraft.shadowmap.client.util.MapUtils;
import com.caucraft.shadowmap.client.util.task.CleanupCounter;
import com.caucraft.shadowmap.client.util.task.CleanupHelper;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.apache.logging.log4j.Level;
import org.lwjgl.BufferUtils;

import java.nio.IntBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

public class RegionContainerImpl implements RegionContainer {

    private static final IntBuffer UPLOAD_INTBUFFER = BufferUtils.createIntBuffer(512 * 512);
    private static final MapFramebuffer UPLOAD_FRAMEBUFFER = new MapFramebuffer(512, 512);

    private final transient MapWorldImpl world;
    private final transient int regionX;
    private final transient int regionZ;
    /**
     * Determines the loading demand on this region, ex whether it is within
     * force-loaded render distance, visible on the fullscreen map, etc.
     */
    private final transient AtomicInteger curFlags;
    private final transient AtomicInteger maxFlags;
    /** Determines which chunks need to be re-rendered. */
    private final transient AtomicIntegerArray chunkRenderFlags;
    private final transient ConcurrentLinkedQueue<Runnable> regionModifications;
    private transient volatile long lastRead;

    private BlocksRegion layerBlocks;
    private MapRegion<?, ?>[] metaRegionArray;
    private final AtomicReference<MapFramebuffer> highResTexture;
    private final AtomicReference<MapFramebuffer> lowResTexture;
    private NbtCompound retainedMeta;

    public RegionContainerImpl(MapWorldImpl world, int regionX, int regionZ) {
        this.world = world;
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.curFlags = new AtomicInteger();
        this.maxFlags = new AtomicInteger();
        this.chunkRenderFlags = new AtomicIntegerArray(32);
        this.regionModifications = new ConcurrentLinkedQueue<>();
        this.metaRegionArray = new MapRegion[world.getMapManager().getStorageKeys().length];
        this.highResTexture = new AtomicReference<>();
        this.lowResTexture = new AtomicReference<>();
    }

    ////////////////////////////////////////////////////////////////////////////
    // <editor-fold desc="Getter/Atomic Setter (thread safe)">

    @Override
    public MapWorldImpl getWorld() {
        return world;
    }

    @Override
    public int getRegionX() {
        return regionX;
    }

    @Override
    public int getRegionZ() {
        return regionZ;
    }

    @Override
    public <RegionType> RegionType getStorage(StorageKey<RegionType> key) {
        return (RegionType) metaRegionArray[key.keyId()];
    }

    public <RegionType extends MapRegion<ChunkType, ChunkNbtContext>,
            ChunkType extends MapChunk<ChunkNbtContext>,
            ChunkNbtContext>
    RegionType getOrUseStorage(StorageKeyImpl<RegionType, ChunkType, ChunkNbtContext> key, RegionType newStorage) {
        RegionType oldStorage = (RegionType) metaRegionArray[key.key];
        if (oldStorage == null) {
            metaRegionArray[key.keyId()] = newStorage;
            return newStorage;
        }
        return oldStorage;
    }

    public BlocksRegion getBlocks() {
        return layerBlocks;
    }

    public BlocksRegion getOrUseBlocks(BlocksRegion newBlocks) {
        if (layerBlocks == null) {
            layerBlocks = newBlocks;
            return newBlocks;
        }
        return layerBlocks;
    }

    public MapFramebuffer getHighResTexture() {
        return highResTexture.get();
    }

    public MapFramebuffer getLowResTexture() {
        return lowResTexture.get();
    }

    public boolean isEmpty() {
        MapRegion<?, ?>[] metaStorage = this.metaRegionArray;
        for (int i = 0; i < metaStorage.length; i++) {
            if (metaStorage[i] != null) {
                return false;
            }
        }
        return layerBlocks == null && highResTexture.get() == null && lowResTexture.get() == null;
    }

    public boolean isModified() {
        if (isFlagSet(RegionFlags.FORCE_SAVE)) {
            return true;
        }
        BlocksRegion blockStorage = layerBlocks;
        if (blockStorage != null && blockStorage.isModified()) {
            return true;
        }
        MapRegion<?, ?>[] metaArray = this.metaRegionArray;
        for (int i = 0; i < metaArray.length; i++) {
            MapRegion<?, ?> metaStorage = metaArray[i];
            if (metaStorage != null && metaStorage.isModified()) {
                return true;
            }
        }
        return false;
    }

    public void setLastRead(long lastReadTimeMs) {
        this.lastRead = lastReadTimeMs;
    }

    public long getLastRead() {
        return lastRead;
    }

    public boolean casFlags(int expectedFlags, int newFlags) {
        if (!curFlags.compareAndSet(expectedFlags, newFlags)) {
            return false;
        }

        int max = maxFlags.get();
        while (!maxFlags.compareAndSet(max, max | newFlags)) {
            max = maxFlags.get();
        }
        return true;
    }

    @Override
    public boolean setFlags(int addedFlags) {
        int oldFlags = curFlags.get();
        if ((oldFlags & addedFlags) == addedFlags) {
            return false;
        }
        while (!curFlags.compareAndSet(oldFlags, oldFlags |= addedFlags)) {
            oldFlags = curFlags.get();
            if ((oldFlags & addedFlags) == addedFlags) {
                return false;
            }
        }
        int max = maxFlags.get();
        while (!maxFlags.compareAndSet(max, max |= oldFlags)) {
            max = maxFlags.get();
        }
        return true;
    }

    @Override
    public boolean clearFlags(int removedFlags) {
        int oldFlags = curFlags.get();
        if ((oldFlags & removedFlags) == 0) {
            return false;
        }
        while (!curFlags.compareAndSet(oldFlags, oldFlags &= ~removedFlags)) {
            oldFlags = curFlags.get();
            if ((oldFlags & removedFlags) == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param flags the flags to check.
     * @return true if all the provided flags are set, false otherwise.
     */
    public boolean isFlagsSet(int flags) {
        return (curFlags.get() & flags) == flags;
    }

    /**
     * @param flags the flags to check.
     * @return the value of the provided flags
     */
    public int getFlags(int flags) {
        return curFlags.get() & flags;
    }

    /**
     * @return the current flags integer
     */
    public int getFlags() {
        return curFlags.get();
    }

    public int getMaxFlags() {
        return maxFlags.get();
    }

    public void reduceMaxFlags() {
        int curLoad = curFlags.get();
        while (true) {
            maxFlags.set(curLoad);
            if (curLoad == curFlags.get()) {
                break;
            }
            curLoad = curFlags.get();
        }
    }

    CleanupHelper isCleanupNeeded(CleanupCounter counter, PerformanceConfig config, long curTimeMs) {
        long timeDiff = curTimeMs - lastRead;
        int maxLoad = getMaxFlags();

        boolean cleanBlockLayer = layerBlocks != null
                && (counter.addBlockMemory(layerBlocks.estimateMemoryUsage()) >> 20 > config.blockMemoryMB.get()
                        || timeDiff > config.blockTimeoutS.get())
                && maxLoad < RegionFlags.FULLMAP_ZOOM_IN.flag;

        boolean metaExists = false;
        int metaUsage = 0;
        MapRegion<?, ?>[] metaArray = this.metaRegionArray;
        if (metaArray != null) {
            for (int i = 0; i < metaArray.length; i++) {
                MapRegion<?, ?> meta = metaArray[i];
                if (metaArray[i] != null) {
                    metaExists = true;
                    metaUsage += meta.estimateMemoryUsage();
                }
            }
        }
        boolean cleanMetaLayer = metaExists
                && (counter.addMetaMemory(metaUsage) > config.metaMemoryMB.get()
                        || timeDiff > config.metaTimeoutS.get())
                && maxLoad < RegionFlags.FULLMAP_ZOOM_OUT.flag;

        int pixelsPerLevel = config.textureMemoryMB.get() * 131072; // memMB * 1MB / 4BpP / 2
        boolean cleanHighResTexture = highResTexture.get() != null
                && (counter.incHighResTextures() << 18 > pixelsPerLevel
                        || timeDiff > config.textureTimeoutS.get())
                && maxLoad < RegionFlags.FULLMAP_ZOOM_IN.flag;
        boolean cleanLowResTexture = lowResTexture.get() != null
                && (counter.incLowResTextures() << 12 > pixelsPerLevel
                        || timeDiff > config.textureTimeoutS.get())
                && maxLoad < RegionFlags.MINIMAP_ZOOM.flag;

        return new CleanupHelper(maxLoad, cleanBlockLayer, cleanMetaLayer, cleanHighResTexture, cleanLowResTexture);
    }

    public NbtCompound getRetainedMeta() {
        return retainedMeta;
    }

    public void setRetainedMeta(NbtCompound retainedMeta) {
        this.retainedMeta = retainedMeta;
    }

    // </editor-fold>

    ////////////////////////////////////////////////////////////////////////////
    // <editor-fold desc="Scheduling Methods (thread safe, schedules locking task)">

    public CompletableFuture<?> scheduleUpdateChunk(World mcWorld, Chunk chunk, ChunkCache chunkCache,
            CeilingType ceilingType, long curTimeMs) {
        regionModifications.add(() -> updateChunk(mcWorld, chunk, chunkCache, ceilingType, curTimeMs));
        return world.scheduleRegionModify(this);
    }

    public CompletableFuture<?> scheduleUpdateBlock(World mcWorld, Chunk chunk, ChunkCache chunkCache, BlockPos pos, BlockState state,
            CeilingType ceilingType, long curTimeMs) {
        BlockPos immutablePos = pos.toImmutable();
        regionModifications.add(() -> updateBlock(mcWorld, chunk, chunkCache, immutablePos, state, ceilingType, curTimeMs));
        return world.scheduleRegionModify(this);
    }

    public CompletableFuture<?> scheduleUpdateSurroundedChunk(World mcWorld, Chunk chunk, ChunkCache chunkCache,
            CeilingType ceilingType, long curTimeMs) {
        regionModifications.add(() -> updateSurroundedChunk(mcWorld, chunk, chunkCache, ceilingType, curTimeMs));
        return world.scheduleRegionModify(this);
    }

    public CompletableFuture<?> scheduleUpdate(Runnable task) {
        regionModifications.add(task);
        return world.scheduleRegionModify(this);
    }

    /**
     * Marks the chunk at the provided relative coordinates for rendering,
     * then schedules the region to be rendered.
     * @param chunkX relative chunk X (only the low 5 bits are considered)
     * @param chunkZ relative chunk Z (only the low 5 bits are considered)
     */
    public void scheduleRerenderChunk(int chunkX, int chunkZ, boolean force) {
        chunkX &= 0x1F;
        chunkZ &= 0x1F;
        int mask = 1 << chunkX;
        int value = chunkRenderFlags.get(chunkZ);
        while (!chunkRenderFlags.compareAndSet(chunkZ, value, value | mask)) {
            value = chunkRenderFlags.get(chunkZ);
        }
        world.scheduleRegionRender(this, force);
    }

    /**
     * Marks all chunks as needing to be re-rendered, then schedules the region
     * to be rendered.
     */
    public void scheduleRerenderAll(boolean force) {
        boolean tryScheduleRender = force;
        for (int z = 0; z < 32; z++) {
            tryScheduleRender |= chunkRenderFlags.getAndSet(z, -1) != -1;
        }
        if (tryScheduleRender) {
            world.scheduleRegionRender(this, force);
        }
    }

    // </editor-fold>

    ////////////////////////////////////////////////////////////////////////////
    // <editor-fold desc="Modify/Update Methods (should write-lock region)">

    @Override
    public <RegionType> RegionType getOrCreateStorage(StorageKey<RegionType> key) {
        return null;
    }

    /**
     * If the region has been modified and should be re-rendered, ensure the
     * framebuffers are valid/have not been closed and nulled.
     */
    private void ensureValidFramebuffers() {
        MapFramebuffer fbuffer;
        while ((fbuffer = highResTexture.get()) == null || fbuffer.isClosed()) {
            MapFramebuffer newBuffer = new MapFramebuffer(512, 512);
            if (highResTexture.compareAndSet(fbuffer, newBuffer)) {
                break;
            }
            newBuffer.close();
        }
        while ((fbuffer = lowResTexture.get()) == null || fbuffer.isClosed()) {
            MapFramebuffer newBuffer = new MapFramebuffer(64, 64);
            if (lowResTexture.compareAndSet(fbuffer, newBuffer)) {
                break;
            }
            newBuffer.close();
        }
    }

    void cleanup(CleanupHelper helper) {
        int maxLoad = this.maxFlags.get();
        if (helper != null && maxLoad > helper.maxLoad()) {
            return;
        }
        reduceMaxFlags();
        boolean ioFailed = isFlagsSet(RegionFlags.IO_FAILED.flag);
        if ((helper == null || helper.blockLayer() && layerBlocks != null && (ioFailed || !layerBlocks.isModified())) && maxLoad < RegionFlags.FULLMAP_ZOOM_IN.flag) {
            layerBlocks = null;
        }
        boolean metaExists = false;
        MapRegion<?, ?>[] metaArray = this.metaRegionArray;
        for (int i = 0; i < metaArray.length && !metaExists; i++) {
            MapRegion<?, ?> metaStorage = metaArray[i];
            if ((helper == null || helper.metaLayer() && metaStorage != null && (ioFailed || !metaStorage.isModified())) && maxLoad < RegionFlags.FULLMAP_ZOOM_OUT.flag) {
                metaArray[i] = null;
            }
        }
        MapFramebuffer fbuffer;
        if ((helper == null || helper.highResTexture()) && (fbuffer = highResTexture.get()) != null && maxLoad < RegionFlags.FULLMAP_ZOOM_IN.flag) {
            while (fbuffer != null && !highResTexture.compareAndSet(fbuffer, null)) {
                fbuffer = highResTexture.get();
            }
            highResTexture.set(null);
            if (fbuffer != null) {
                fbuffer.close();
            }
        }
        if ((helper == null || helper.lowResTexture()) && (fbuffer = lowResTexture.get()) != null && maxLoad < RegionFlags.MINIMAP_ZOOM.flag) {
            while (fbuffer != null && !lowResTexture.compareAndSet(fbuffer, null)) {
                fbuffer = lowResTexture.get();
            }
            lowResTexture.set(null);
            if (fbuffer != null) {
                fbuffer.close();
            }
        }
    }

    private void updateChunk(World world, Chunk chunk, ChunkCache chunkCache, CeilingType ceilingType, long curTimeMs) {
        if (layerBlocks == null) {
            layerBlocks = new BlocksRegion(this);
        }
        ChunkPos chunkPos = chunk.getPos();
        boolean rerender = false;
        boolean changed = false;
        {
            MapChunk<?> mapChunk = layerBlocks.getChunk(chunkPos.x, chunkPos.z, true);
            if (mapChunk != null) {
                changed = mapChunk.updateChunk(world, chunk, chunkCache, ceilingType, curTimeMs);
                if (changed) {
                    mapChunk.setLastModified(curTimeMs);
                }
            }
            changed |= layerBlocks.updateChunk(world, chunk, chunkCache, ceilingType, curTimeMs);
            if (changed) {
                layerBlocks.setLastModified(curTimeMs);
            }
            rerender |= changed;
        }

        MapRegion<?, ?>[] metaArray = metaRegionArray;
        for (int i = 0; i < metaArray.length; i++) {
            MapRegion<?, ?> metaRegion = metaArray[i];
            if (metaRegion == null) {
                metaArray[i] = metaRegion = this.world.getMapManager().getStorageKeys()[i].user.createStorage(this);
            }
            changed = false;
            MapChunk<?> mapChunk = metaRegion.getChunk(chunkPos.x, chunkPos.z, true);
            if (mapChunk != null) {
                changed = mapChunk.updateChunk(world, chunk, chunkCache, ceilingType, curTimeMs);
                if (changed) {
                    mapChunk.setLastModified(curTimeMs);
                }
            }
            changed |= metaRegion.updateChunk(world, chunk, chunkCache, ceilingType, curTimeMs);
            if (changed) {
                metaRegion.setLastModified(curTimeMs);
            }
            rerender |= changed;
        }

        if (rerender) {
            for (int chunkZOffset = -1; chunkZOffset <= 1; chunkZOffset++) {
                for (int chunkXOffset = -1; chunkXOffset <= 1; chunkXOffset++) {
                    int chunkX = chunkPos.x + chunkXOffset;
                    int chunkZ = chunkPos.z + chunkZOffset;
                    int regionX = chunkX >> 5;
                    int regionZ = chunkZ >> 5;
                    if (regionX == this.regionX && regionZ == this.regionZ) {
                        scheduleRerenderChunk(chunkX, chunkZ, false);
                    } else {
                        RegionContainerImpl otherRegion = this.world.getRegion(regionX, regionZ, false, false);
                        if (otherRegion != null) {
                            otherRegion.scheduleRerenderChunk(chunkX, chunkZ, false);
                        }
                    }
                }
            }
        }
    }

    private void updateSurroundedChunk(World world, Chunk chunk, ChunkCache chunkCache,
            CeilingType ceilingType, long curTimeMs) {
        if (layerBlocks == null) {
            layerBlocks = new BlocksRegion(this);
        }
        ChunkPos chunkPos = chunk.getPos();
        boolean changed = false;
        boolean rerender = false;
        {
            MapChunk<?> mapChunk = layerBlocks.getChunk(chunkPos.x, chunkPos.z, true);
            if (mapChunk != null) {
                changed = mapChunk.updateSurroundedChunk(world, chunk, chunkCache, ceilingType, curTimeMs);
                if (changed) {
                    mapChunk.setLastModified(curTimeMs);
                }
            }
            changed |= layerBlocks.updateSurroundedChunk(world, chunk, chunkCache, ceilingType, curTimeMs);
            if (changed) {
                layerBlocks.setLastModified(curTimeMs);
            }
            rerender |= changed;
        }

        MapRegion<?, ?>[] metaArray = metaRegionArray;
        for (int i = 0; i < metaArray.length; i++) {
            MapRegion<?, ?> metaRegion = metaArray[i];
            if (metaRegion == null) {
                metaArray[i] = metaRegion = this.world.getMapManager().getStorageKeys()[i].user.createStorage(this);
            }
            changed = false;
            MapChunk<?> mapChunk = metaRegion.getChunk(chunkPos.x, chunkPos.z, true);
            if (mapChunk != null) {
                changed = mapChunk.updateSurroundedChunk(world, chunk, chunkCache, ceilingType, curTimeMs);
                if (changed) {
                    mapChunk.setLastModified(curTimeMs);
                }
            }
            changed |= metaRegion.updateSurroundedChunk(world, chunk, chunkCache, ceilingType, curTimeMs);
            if (changed) {
                metaRegion.setLastModified(curTimeMs);
            }
            rerender |= changed;
        }

        if (rerender) {
            scheduleRerenderChunk(chunkPos.x, chunkPos.z, false);
        }
    }

    private void updateBlock(World world, Chunk chunk, ChunkCache chunkCache, BlockPos pos, BlockState state,
            CeilingType ceilingType, long curTimeMs) {
        if (layerBlocks == null) {
            layerBlocks = new BlocksRegion(this);
            ShadowMap.getLogger().warn("Updated block in a region with no blocks layer: " + regionX + " " + regionZ);
        }

        ChunkPos chunkPos = chunk.getPos();
        boolean rerender = false;
        boolean changed = false;
        {
            MapChunk<?> mapChunk = layerBlocks.getChunk(chunkPos.x, chunkPos.z, true);
            if (mapChunk != null) {
                changed = mapChunk.updateBlock(world, chunk, chunkCache, ceilingType, pos, state, curTimeMs);
                if (changed) {
                    mapChunk.setLastModified(curTimeMs);
                }
            }
            changed |= layerBlocks.updateBlock(world, chunk, chunkCache, ceilingType, pos, state, curTimeMs);
            if (changed) {
                layerBlocks.setLastModified(curTimeMs);
            }
            rerender |= changed;
        }

        MapRegion<?, ?>[] metaArray = metaRegionArray;
        for (int i = 0; i < metaArray.length; i++) {
            MapRegion<?, ?> metaRegion = metaArray[i];
            if (metaRegion == null) {
                metaArray[i] = metaRegion = this.world.getMapManager().getStorageKeys()[i].user.createStorage(this);
            }
            changed = false;
            MapChunk<?> mapChunk = metaRegion.getChunk(chunkPos.x, chunkPos.z, true);
            if (mapChunk != null) {
                changed = mapChunk.updateBlock(world, chunk, chunkCache, ceilingType, pos, state, curTimeMs);
                if (changed) {
                    mapChunk.setLastModified(curTimeMs);
                }
            }
            changed |= metaRegion.updateBlock(world, chunk, chunkCache, ceilingType, pos, state, curTimeMs);
            if (changed) {
                metaRegion.setLastModified(curTimeMs);
            }
            rerender |= changed;
        }

        if (rerender) {
            scheduleRerenderChunk(chunkPos.x, chunkPos.z, false);

            for (int blockZOffset = -1; blockZOffset <= 1; blockZOffset += 2) {
                for (int blockXOffset = -1; blockXOffset <= 1; blockXOffset += 2) {
                    int chunkX = (pos.getX() + blockXOffset) >> 4;
                    int chunkZ = (pos.getZ() + blockZOffset) >> 4;
                    if (chunkX == chunkPos.x && chunkZ == chunkPos.z) {
                        continue;
                    }
                    int regionX = chunkX >> 5;
                    int regionZ = chunkZ >> 5;
                    if (regionX == this.regionX && regionZ == this.regionZ) {
                        scheduleRerenderChunk(chunkX, chunkZ, false);
                    } else {
                        RegionContainerImpl otherRegion = this.world.getRegion(regionX, regionZ, false, false);
                        if (otherRegion != null) {
                            otherRegion.scheduleRerenderChunk(chunkX, chunkZ, false);
                        }
                    }
                }
            }
        }
    }

    void processUpdates() {
        clearFlag(RegionFlags.MODIFY_SCHEDULED);
        Runnable update;
        try {
            while ((update = regionModifications.poll()) != null) {
                update.run();
            }
        } catch (RuntimeException ex) {
            world.scheduleRegionModify(this);
            throw ex;
        }
    }

    // </editor-fold>

    ////////////////////////////////////////////////////////////////////////////
    // <editor-fold desc="Render Buffer Methods (should read-lock region)">

    /**
     * Renders the region, initializing its framebuffers if necessary, compiling
     * vertex data, and scheduling buffer uploads on the render thread.
     */
    public void renderRegion() {
        clearFlag(RegionFlags.RENDER_SCHEDULED);
        BlocksRegion layerBlocks = this.layerBlocks;
        if (layerBlocks == null) {
            return;
        }
        ensureValidFramebuffers();
        RegionRenderContextImpl renderContext;
        try {
            renderContext = new RegionRenderContextImpl(world, this, layerBlocks,
                    world.getMapManager().getRenderBufferPool().take(),
                    new int[4]);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        }
        IntList list = new IntArrayList();
        boolean isFullRender = true;
        try {
            int[] argbBuffer = new int[4];
            for (int z = 0; z < 32; z++) {
                int chunkRowFlags = chunkRenderFlags.get(z);
                if (chunkRowFlags == 0) {
                    isFullRender = false;
                    continue;
                }
                chunkRow:
                for (int x = 0; x < 32; x++) {
                    int chunkMask = 1 << x;
                    if ((chunkRowFlags & chunkMask) == 0) {
                        isFullRender = false;
                        continue;
                    }
                    while (!chunkRenderFlags.compareAndSet(z, chunkRowFlags, chunkRowFlags &= ~chunkMask)) {
                        chunkRowFlags = chunkRenderFlags.get(z);
                        if ((chunkRowFlags & chunkMask) == 0) {
                            isFullRender = false;
                            continue chunkRow;
                        }
                    }
                    list.add(x << 20 | z << 4);
                    if (renderContext.beginChunk(x, z)) {
                        renderContext.chunk().render(renderContext);
                    }
                }
            }
        } catch (Throwable thrown) {
            ShadowMap.getLogger().log(Level.WARN, "Exception thrown while rendering, buffer may not be empty.");
            world.getMapManager().getRenderBufferPool().release(renderContext.imageBuffer);
            throw thrown;
        }
        boolean wasFullRender = isFullRender;
        RenderSystem.recordRenderCall(() -> renderAndReleaseBuffer(renderContext.imageBuffer, list, wasFullRender));
    }

    // </editor-fold>

    ////////////////////////////////////////////////////////////////////////////
    // <editor-fold desc="Render Upload Methods (schedule on render thread)">

    private void renderAndReleaseBuffer(int[] imageBuffer, IntList chunkCoordList, boolean wasFullRender) {
        try {
            IntBuffer uploadIntBuffer = UPLOAD_INTBUFFER;
            MapFramebuffer uploadFramebuffer = UPLOAD_FRAMEBUFFER;
            if (!uploadFramebuffer.isInitialized()) {
                uploadFramebuffer.resize(512, 512, MinecraftClient.IS_SYSTEM_MAC);
            }

            MapFramebuffer highRes = highResTexture.get();
            if (highRes == null || highRes.isClosed()) {
                return;
            }
            if (!highRes.isInitialized()) {
                highRes.resize(highRes.getWidth(), highRes.getHeight(), true);
                if (!wasFullRender) {
                    scheduleRerenderAll(false);
                }
            }

            // Render high-res map
            if (wasFullRender && !chunkCoordList.isEmpty()) {
                // Upload modified directly to highres texture
                uploadIntBuffer.clear();
                uploadIntBuffer.put(imageBuffer);
                uploadIntBuffer.flip();
                MapUtils.uploadTexture(highRes.getColorAttachment(), uploadIntBuffer, 512, 512);
            } else {
                // Upload modified to intermediate texture
                uploadIntBuffer.clear();
                uploadIntBuffer.put(imageBuffer);
                uploadIntBuffer.flip();
                MapUtils.uploadTexture(uploadFramebuffer.getColorAttachment(), uploadIntBuffer, 512, 512);

                // Blit texture to region buffer
                GlStateManager._glBindFramebuffer(GlConst.GL_READ_FRAMEBUFFER, uploadFramebuffer.fbo);
                GlStateManager._glBindFramebuffer(GlConst.GL_DRAW_FRAMEBUFFER, highRes.fbo);
                for (int i = chunkCoordList.size() - 1; i >= 0; i--) {
                    int combined = chunkCoordList.getInt(i);
                    int x1 = combined >>> 16 & 0xFFFF;
                    int z1 = combined & 0xFFFF;
                    int x2 = x1 + 16;
                    int z2 = z1 + 16;
                    GlStateManager._glBlitFrameBuffer(x1, z1, x2, z2, x1, z1, x2, z2, GlConst.GL_COLOR_BUFFER_BIT, GlConst.GL_NEAREST);
                }
            }
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, 0);

            MapFramebuffer lowRes = lowResTexture.get();
            if (lowRes == null || lowRes.isClosed()) {
                return;
            }
            if (!lowRes.isInitialized()) {
                lowRes.resize(lowRes.getWidth(), lowRes.getHeight(), true);
            }

            // Blit to low res map
            lowRes.beginWrite(true);
            RenderSystem.clear(GlConst.GL_COLOR_BUFFER_BIT, true);
            lowRes.endWrite();
            GlStateManager._glBindFramebuffer(GlConst.GL_READ_FRAMEBUFFER, highRes.fbo);
            GlStateManager._glBindFramebuffer(GlConst.GL_DRAW_FRAMEBUFFER, lowRes.fbo);
            GlStateManager._glBlitFrameBuffer(0, 0, highRes.textureWidth, highRes.textureHeight, 0, 0, lowRes.textureWidth, lowRes.textureHeight, GlConst.GL_COLOR_BUFFER_BIT, GlConst.GL_LINEAR);
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, 0);
        } finally {
            world.getMapManager().getRenderBufferPool().release(imageBuffer);
        }
    }

    // </editor-fold>
}
