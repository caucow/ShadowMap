package com.caucraft.shadowmap.client.map;

import com.caucraft.shadowmap.api.map.CeilingType;
import com.caucraft.shadowmap.api.map.MapChunk;
import com.caucraft.shadowmap.api.map.MapRegion;
import com.caucraft.shadowmap.api.map.MapWorld;
import com.caucraft.shadowmap.api.map.RegionFlags;
import com.caucraft.shadowmap.api.util.ChunkCache;
import com.caucraft.shadowmap.api.util.MergeResult;
import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.api.util.RenderArea;
import com.caucraft.shadowmap.client.config.MinimapConfig;
import com.caucraft.shadowmap.client.gui.MinimapHud;
import com.caucraft.shadowmap.client.util.ApiUser;
import com.caucraft.shadowmap.client.util.task.CleanupHelper;
import com.caucraft.shadowmap.client.util.io.ByteBufferInputStream;
import com.caucraft.shadowmap.client.util.io.ByteBufferOutputStream;
import com.caucraft.shadowmap.client.util.MapUtils;
import com.caucraft.shadowmap.api.util.RegistryWrapper;
import com.caucraft.shadowmap.api.util.WorldKey;
import com.caucraft.shadowmap.client.waypoint.WorldWaypointManager;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registry;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;

public class MapWorldImpl implements MapWorld {

    private final MapManagerImpl mapManager;
    private final WorldKey worldKey;
    private final Path worldDirectory;
    private WeakReference<World> world;
    private Registry<Block> blockRegistry;
    private RegistryWrapper<Biome> biomeRegistry;
    private final Long2ObjectLinkedOpenHashMap<RegionContainerImpl> regionMap; // Should only be accessed/modified from client thread
    private RenderArea[] priorityAreas;
    private CeilingType ceilingType;
    private WorldWaypointManager waypointManager;
    private final BlockingQueue<WeakReference<Object>> forceLoaders;
    private CompletableFuture<?> loadFuture;

    public MapWorldImpl(MapManagerImpl mapManager, WorldKey worldKey, File mapsDirectory, World world, Registry<Block> blockRegistry, Registry<Biome> biomeRegistry) {
        this.mapManager = mapManager;
        this.worldKey = worldKey;
        this.worldDirectory = MapUtils.getWorldDirectory(mapsDirectory, worldKey).toPath();
        this.world = new WeakReference<>(world);
        this.blockRegistry = blockRegistry;
        this.biomeRegistry = new RegistryWrapper<>(biomeRegistry);
        this.regionMap = new Long2ObjectLinkedOpenHashMap<>();
        this.priorityAreas = new RenderArea[LoadLevel.values().length];
        if (world != null) {
            this.ceilingType = world.getDimension().hasCeiling() ? CeilingType.ROOFED : CeilingType.OPEN;
        } else {
            this.ceilingType = CeilingType.OPEN;
        }
        Arrays.fill(this.priorityAreas, RenderArea.EMPTY_AREA);
        this.waypointManager = new WorldWaypointManager();
        this.forceLoaders = new LinkedBlockingQueue<>();
        this.loadFuture = scheduleWaypointLoad();
    }

    /**
     * Gets an object used to force-load the world. As long as the object is
     * retained in memory, the world will not be unloaded. References to the
     * object returned can be set to null to lazily clear the force-load, or the
     * reference can be passed to {@link #releaseForceLoader(Object)} to quickly
     * allow unloading.
     * @return An object used as a force-load token.
     */
    public Object getForceLoader() {
        Object loader = new Object();
        forceLoaders.add(new WeakReference<>(loader));
        return loader;
    }

    /**
     * Clears the force-load set by the provided token, if one exists.
     * @param forceLoader a force-load token provided by
     * {@link #getForceLoader()}.
     */
    public void releaseForceLoader(Object forceLoader) {
        Lock globalLock = mapManager.getGlobalLock().writeLock();
        try {
            globalLock.lock();
            forceLoaders.removeIf((ref) -> ref.get() == forceLoader);
        } finally {
            globalLock.unlock();
        }
    }

    public void waitForLoad() throws InterruptedException {
        CompletableFuture<?> loadFuture = this.loadFuture;
        if (loadFuture == null || loadFuture.isDone()) {
            return;
        }
        try {
            loadFuture.get();
        } catch (ExecutionException ignored) {}
    }

    /**
     * @return true if there are no regions loaded in this world.
     */
    public boolean isEmpty() {
        BlockingQueue<WeakReference<Object>> forceLoaders = this.forceLoaders;
        if (!forceLoaders.isEmpty()) {
            Lock globalLock = mapManager.getGlobalLock().writeLock();
            WeakReference<Object> nextRef;
            try {
                globalLock.lock();
                while ((nextRef = forceLoaders.peek()) != null && nextRef.get() == null) {
                    forceLoaders.poll();
                }
            } finally {
                globalLock.unlock();
            }
        }
        return regionMap.isEmpty() && !waypointManager.isModified() && forceLoaders.isEmpty();
    }

    /**
     * Gets an immutable list of all regions present in this world at this time.
     * Synchronizes on the map manager to make sure the map isn't written to
     * while values are being copied to the list.
     * @return a list with all regions loaded by the world.
     */
    public List<RegionContainerImpl> getRegions() {
        synchronized (mapManager) {
            return ImmutableList.copyOf(regionMap.values());
        }
    }

    public MapManagerImpl getMapManager() {
        return mapManager;
    }

    public WorldKey getWorldKey() {
        return worldKey;
    }

    public World getWorld() {
        return world.get();
    }

    public CeilingType getCeilingType() {
        return ceilingType;
    }

    public WorldWaypointManager getWaypointManager() {
        return waypointManager;
    }

    public void updateWorldAndRegistries(World world, Registry<Block> blockRegistry, Registry<Biome> biomeRegistry) {
        this.world = new WeakReference<>(world);
        if (world != null) {
            this.ceilingType = world.getDimension().hasCeiling() ? CeilingType.ROOFED : CeilingType.OPEN;
        } else {
            this.ceilingType = CeilingType.OPEN;
        }
        this.blockRegistry = blockRegistry;
        this.biomeRegistry.setWrapped(biomeRegistry);
    }

    public Registry<Block> getBlockRegistry() {
        return blockRegistry;
    }

    public RegistryWrapper<Biome> getBiomeRegistry() {
        return biomeRegistry;
    }

    void scheduleUpdateChunk(World world, Chunk chunk, ChunkCache chunkCache, long curTimeMs) {
        ChunkPos chunkPos = chunk.getPos();
        RegionContainerImpl region = getRegion(chunkPos.x >> 5, chunkPos.z >> 5, true, true);
        region.scheduleUpdateChunk(world, chunk, chunkCache, ceilingType, curTimeMs);
    }

    void scheduleUpdateSurroundedChunk(World world, Chunk chunk, ChunkCache chunkCache, long curTimeMs) {
        ChunkPos chunkPos = chunk.getPos();
        RegionContainerImpl region = getRegion(chunkPos.x >> 5, chunkPos.z >> 5, true, true);
        region.scheduleUpdateSurroundedChunk(world, chunk, chunkCache, ceilingType, curTimeMs);
    }

    void scheduleUpdateBlocks(World world, Chunk chunk, ChunkCache chunkCache, BlockPos pos, BlockState state, long curTimeMs) {
        ChunkPos chunkPos = chunk.getPos();
        RegionContainerImpl region = getRegion(chunkPos.x >> 5, chunkPos.z >> 5, true, true);
        region.scheduleUpdateBlock(world, chunk, chunkCache, pos, state, ceilingType, curTimeMs);
    }

    /**
     * Gets the region at the provided region X and Z coordinates.
     * @param regionX the region's X coordinate.
     * @param regionZ the region's Z coordinate.
     * @param create set to true if a new region can be created if one does not
     * exist (ex. if world chunks in the region are loaded and need to be
     * mapped)
     * @param load set to true if, when a region is missing and {@code create}
     * is true, the region should also be scheduled to be loaded and merged with
     * files on disk.
     * @return The map region at the provided coordinates, or null if there is
     * none and {@code create} is false.
     */
    public RegionContainerImpl getRegion(int regionX, int regionZ, boolean create, boolean load) {
        // TODO handle differences between create and load so a region can be loaded in the future (or not) without needing to be created now
        // for now create |= load
        // ALSO update javadoc when this behavior is changed.
        create |= load;

        RegionContainerImpl region;
        long regionKey = ((long) regionZ << 32) | ((long) regionX & 0xFFFF_FFFFL);
        region = regionMap.get(regionKey);
        if (region == null && create) {
            synchronized (mapManager) {
                region = new RegionContainerImpl(this, regionX, regionZ);
                RenderArea[] localPriorities = this.priorityAreas;
                for (LoadLevel enumValue : LoadLevel.values()) {
                    if (localPriorities[enumValue.ordinal()].containsRegion(regionX, regionZ)) {
                        region.setFlag(enumValue.loadFlag);
                    }
                }
                regionMap.put(regionKey, region);
            }
            if (load) {
                scheduleRegionLoad(region);
            }
        }
        if (region != null) {
            region.setLastRead(mapManager.lastTickTime);
        }
        return region;
    }

    private boolean removeRegion(RegionContainerImpl region) {
        if (region == null) {
            return false;
        }
        long regionKey = ((long) region.getRegionZ() << 32) | ((long) region.getRegionX() & 0xFFFF_FFFFL);
        synchronized (mapManager) {
            boolean empty = region.isEmpty();
            boolean removed = empty && regionMap.remove(regionKey, region);
            return removed;
        }
    }

    /**
     * Sets the priority render area. Any regions queued to render will be
     * prioritized based on their distance to the center of the provided area.
     *
     * @param loadLevel the load level to prioritize.
     * @param newArea the new priority render area. Use null to de-prioritize
     * the given load level.
     */
    public void setRenderPriorityArea(LoadLevel loadLevel, RenderArea newArea) {
        synchronized (mapManager) {
            if (loadLevel == null) {
                for (LoadLevel enumValue : LoadLevel.values()) {
                    setRenderPriorityArea(enumValue, newArea);
                }
                return;
            }
            if (newArea == null) {
                newArea = RenderArea.EMPTY_AREA;
            }
            int areaIndex = loadLevel.ordinal();
            RenderArea[] oldAreas = this.priorityAreas;
            if (newArea.equals(oldAreas[areaIndex])) {
                return;
            }
            RenderArea[] newAreas = Arrays.copyOf(oldAreas, oldAreas.length);
            newAreas[areaIndex] = newArea;
            this.priorityAreas = newAreas;
            RenderArea oldArea = oldAreas[areaIndex];
            long curTime = ShadowMap.getLastTickTimeS();
            mapManager.scheduleResortPriority();
            for (int z = oldArea.minZ(); z <= oldArea.maxZ(); z++) {
                for (int x = oldArea.minX(); x <= oldArea.maxX(); x++) {
                    if (!newArea.containsRegion(x, z)) {
                        RegionContainerImpl region = getRegion(x, z, false, false);
                        if (region != null) {
                            region.clearFlag(loadLevel.loadFlag);
                        }
                    }
                }
            }
            if (newArea == RenderArea.EMPTY_AREA) {
                return;
            }
            for (int z = newArea.minZ(); z <= newArea.maxZ(); z++) {
                for (int x = newArea.minX(); x <= newArea.maxX(); x++) {
                    if (!oldArea.containsRegion(x, z)) {
                        RegionContainerImpl region = getRegion(x, z, false, true);
                        if (region != null) {
                            region.setFlag(loadLevel.loadFlag);
                            scheduleRegionLoad(region);
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the minimum render priority (low value = highest priority) of the
     * provided region across all of this world's load levels.
     * @param regionX x coordinate of the region to get the priority of
     * @param regionZ z coordinate of the region to get the priority of
     * @return the lowest priority value of the provided region across all of
     * this world's load levels (lower value = higher priority)
     */
    public long getRenderPriority(int regionX, int regionZ) {
        return RenderArea.getRenderPriority(this.priorityAreas, regionX, regionZ);
    }

    public RenderArea getRenderArea(LoadLevel loadLevel) {
        return priorityAreas[loadLevel.ordinal()];
    }

    public RenderArea[] getRenderAreas() {
        RenderArea[] localArray = priorityAreas;
        return Arrays.copyOf(localArray, localArray.length);
    }

    public void setPlayerPosition(int blockX, int blockZ) {
        int regionX = blockX >> 9;
        int regionZ = blockZ >> 9;
        RenderArea currentPrioArea = getRenderArea(LoadLevel.RENDER_DISTANCE_FORCED);
        if (!currentPrioArea.containsRegion(regionX, regionZ)
                || regionX != currentPrioArea.centerX()
                || regionZ != currentPrioArea.centerZ()) {
            setRenderPriorityArea(LoadLevel.RENDER_DISTANCE_FORCED, new RenderArea(regionX - 2, regionZ - 2, regionX + 2, regionZ + 2));
        }
    }

    public void setCameraPosition(MinimapConfig config, int blockX, int blockZ) {
        int regionX = blockX >> 9;
        int regionZ = blockZ >> 9;
        RenderArea currentPrioArea = getRenderArea(LoadLevel.MINIMAP_ZOOM);
        int regionRadius = MathHelper.ceil(MinimapHud.getRadius(config) / 512.0F / config.zoom.get());
        if (config.shape.get() == MinimapConfig.Shape.SQUARE && !config.lockNorth.get()) {
            regionRadius *= 1.4142135623730950488016887242097;
        }
        if (!currentPrioArea.containsRegion(regionX, regionZ)
                || regionX != currentPrioArea.centerX() || regionRadius + regionRadius + 1 != currentPrioArea.width()
                || regionZ != currentPrioArea.centerZ() || regionRadius + regionRadius + 1 != currentPrioArea.height()) {
            setRenderPriorityArea(LoadLevel.MINIMAP_ZOOM, new RenderArea(regionX - regionRadius, regionZ - regionRadius, regionX + regionRadius, regionZ + regionRadius));
        }
    }

    public void clearFullmapFocus() {
        setRenderPriorityArea(LoadLevel.FULL_MAP_ZOOM_IN, null);
        setRenderPriorityArea(LoadLevel.FULL_MAP_ZOOM_OUT, null);
    }

    public void setFullmapFocus(int blockCenterX, int blockCenterZ, int screenWidth, int screenHeight, float zoom) {
        int regionMinX = MathHelper.floor(blockCenterX - screenWidth * 0.5 / zoom) >> 9;
        int regionMinZ = MathHelper.floor(blockCenterZ - screenHeight * 0.5 / zoom) >> 9;
        int regionMaxX = MathHelper.ceil(blockCenterX + screenWidth * 0.5 / zoom) >> 9;
        int regionMaxZ = MathHelper.ceil(blockCenterZ + screenHeight * 0.5 / zoom) >> 9;
        if (zoom <= 0.125) {
            setRenderPriorityArea(LoadLevel.FULL_MAP_ZOOM_OUT, new RenderArea(regionMinX, regionMinZ, regionMaxX, regionMaxZ));
            setRenderPriorityArea(LoadLevel.FULL_MAP_ZOOM_IN, null);
        } else {
            setRenderPriorityArea(LoadLevel.FULL_MAP_ZOOM_IN, new RenderArea(regionMinX, regionMinZ, regionMaxX, regionMaxZ));
            setRenderPriorityArea(LoadLevel.FULL_MAP_ZOOM_OUT, null);
        }
    }

    /**
     * Schedules the region to process block updates on the map manager's modify
     * threads. If the region is already flagged as scheduled for update it
     * will not be rescheduled.
     * @param region the region to render
     */
    CompletableFuture<?> scheduleRegionModify(RegionContainerImpl region) {
        if (!region.setFlag(RegionFlags.MODIFY_SCHEDULED)) {
            return CompletableFuture.completedFuture(null);
        }
        return mapManager.executeModifyTask(region.getRegionX(), region.getRegionZ(), () -> {
            region.processUpdates();
            return null;
        });
    }

    CompletableFuture<?> scheduleRegionCleanup(RegionContainerImpl region, CleanupHelper cleanupHelper, CompletableFuture<Void> completeOnFinish) {
        return mapManager.executeModifyTask(region.getRegionX(), region.getRegionZ(), new RegionCleanupTask(region, cleanupHelper, completeOnFinish));
    }

    /**
     * Schedules the region to be rendered on the map manager's render threads.
     * If the region is already flagged as needing to be rendered it will not be
     * rescheduled.
     * @param region the region to render
     */
    void scheduleRegionRender(RegionContainerImpl region, boolean force) {
        if (!region.setFlag(RegionFlags.RENDER_SCHEDULED) && !force) {
            return;
        }
        RegionRenderTask renderTask = new RegionRenderTask(region.getRegionX(), region.getRegionZ());
        mapManager.executeRenderTask(this, region.getRegionX(), region.getRegionZ(), renderTask);
    }

    /**
     * Sets the region's {@link RegionFlags#LOAD_NEEDED} flag and schedules the
     * region to be loaded and merged from disk.
     * @param region the region to load.
     */
    CompletableFuture<?> scheduleRegionLoad(RegionContainerImpl region) {
        if (!region.setFlag(RegionFlags.LOAD_NEEDED)) {
            return CompletableFuture.completedFuture(null);
        }
        int regionX = region.getRegionX();
        int regionZ = region.getRegionZ();
        return mapManager.executeIOTask(this, regionX, regionZ, new RegionLoadTask(regionX, regionZ), true);
    }

    /**
     * Schedules the region to be saved to disk, potentially loading and merging
     * with a more recent map of the region in the process.
     * @param region the region to save.
     */
    public void scheduleRegionSave(RegionContainerImpl region, CompletableFuture<Integer> completeOnFinish) {
        if (region.setFlag(RegionFlags.SAVE_SCHEDULED) || completeOnFinish != null) {
            mapManager.executeIOTask(this, region.getRegionX(), region.getRegionZ(), new RegionSaveTask(region, completeOnFinish), false);
        }
    }

    CompletableFuture<?> scheduleWaypointLoad() {
        return mapManager.executeGlobalIOTask(new WaypointLoadTask());
    }

    public CompletableFuture<?> scheduleWaypointSave() {
        return mapManager.executeGlobalIOTask(new WaypointSaveTask());
    }

    private static String getRegionFileName(int regionX, int regionZ) {
        return regionX + "," + regionZ + ".dat";
    }

    private class RegionCleanupTask implements Callable<Void> {
        private final RegionContainerImpl region;
        private final CleanupHelper cleanupHelper;
        private final CompletableFuture<Void> completeOnFinish;

        RegionCleanupTask(RegionContainerImpl region, CleanupHelper cleanupHelper, CompletableFuture<Void> completeOnFinish) {
            this.region = region;
            this.cleanupHelper = cleanupHelper;
            this.completeOnFinish = completeOnFinish;
        }

        @Override
        public Void call() {
            try {
                if (cleanupHelper == null) {
                    region.cleanup(null);
                    removeRegion(region);
                    return null;
                }
                region.cleanup(cleanupHelper);
                if (region.isEmpty() && !region.isFlagsSet(RegionFlags.RENDER_DISTANCE_FORCED.flag)) {
                    removeRegion(region);
                }
            } finally {
                if (completeOnFinish != null) {
                    completeOnFinish.complete(null);
                }
            }
            return null;
        }
    }

    /**
     * Helper class for rendering regions that allows the render queue to be
     * dumped and re-ordered in case render order needs to be changed (ex. after
     * a teleport or opening/moving the world map to a new location).
     */
    private class RegionRenderTask implements Callable<Void> {
        private final int regionX, regionZ;

        public RegionRenderTask(int regionX, int regionZ) {
            this.regionX = regionX;
            this.regionZ = regionZ;
        }

        @Override
        public Void call() {
            RegionContainerImpl region = getRegion(regionX, regionZ, false, false);
            if (region != null) {
                region.renderRegion();
            }
            return null;
        }
    }

    void rerenderSurrounding(RegionContainerImpl region) {
        region.scheduleRerenderAll(false);
        RegionContainerImpl neighbor;
        if ((neighbor = getRegion(region.getRegionX(), region.getRegionZ() - 1, false, false)) != null) {
            for (int i = 0; i < 32; i++) {
                neighbor.scheduleRerenderChunk(i, 31, false);
            }
        }
        if ((neighbor = getRegion(region.getRegionX() + 1, region.getRegionZ(), false, false)) != null) {
            for (int i = 0; i < 32; i++) {
                neighbor.scheduleRerenderChunk(0, i, false);
            }
        }
        if ((neighbor = getRegion(region.getRegionX(), region.getRegionZ() + 1, false, false)) != null) {
            for (int i = 0; i < 32; i++) {
                neighbor.scheduleRerenderChunk(i, 0, false);
            }
        }
        if ((neighbor = getRegion(region.getRegionX() - 1, region.getRegionZ(), false, false)) != null) {
            for (int i = 0; i < 32; i++) {
                neighbor.scheduleRerenderChunk(31, i, false);
            }
        }
        if ((neighbor = getRegion(region.getRegionX() - 1, region.getRegionZ() - 1, false, false)) != null) {
            neighbor.scheduleRerenderChunk(31, 31, false);
        }
        if ((neighbor = getRegion(region.getRegionX() - 1, region.getRegionZ() + 1, false, false)) != null) {
            neighbor.scheduleRerenderChunk(31, 0, false);
        }
        if ((neighbor = getRegion(region.getRegionX() + 1, region.getRegionZ() - 1, false, false)) != null) {
            neighbor.scheduleRerenderChunk(0, 31, false);
        }
        if ((neighbor = getRegion(region.getRegionX() + 1, region.getRegionZ() + 1, false, false)) != null) {
            neighbor.scheduleRerenderChunk(0, 0, false);
        }
    }

    static <RegionType extends MapRegion<ChunkType, ChunkNbtContext>,
            ChunkType extends MapChunk<ChunkNbtContext>,
            ChunkNbtContext>
    MergeResult loadStorage(StorageKeyImpl<RegionType, ChunkType, ChunkNbtContext> storageKey, RegionContainerImpl region, NbtCompound storageNbt, long lastSaved) throws IOException {
        RegionType newStorage;
        if (storageNbt != null) {
            newStorage = storageKey.createStorage(region);
            newStorage.loadFromNbt(storageNbt);
            newStorage.setLastSaved(lastSaved);
        } else {
            newStorage = null;
        }
        RegionType oldStorage = region.getOrUseStorage(storageKey, newStorage);
        if (newStorage == null) {
            return oldStorage != null
                    ? MergeResult.getResult().usedThis()
                    : MergeResult.getResult();
        }
        if (oldStorage == newStorage) {
            return MergeResult.getResult().usedOther().renderNeeded();
        }
        if (oldStorage != null) {
            return oldStorage.mergeFrom(newStorage);
        }
        // This shouldn't be reached.
        // If new/loaded exists, either it will replace a null in memory (getOrUse), or it will be merged into memory
        // If it doesn't exist, return will be based on whether in memory storage is null.
        return MergeResult.getResult().usedThis();
    }

    private class WaypointLoadTask implements Callable<Void> {
        public WaypointLoadTask() {}

        @Override
        public Void call() {
            ByteBuffer buffer = null;
            FileChannel waypointsChannel = null;

            try {
                buffer = mapManager.getIOBufferPool().take();
                Path waypointsPath = worldDirectory.resolve("world.dat");
                boolean waypointsExists = Files.exists(waypointsPath);
                long waypointsModified = 0;
                long waypointsSize = 0;

                if (waypointsExists) {
                    waypointsChannel = FileChannel.open(waypointsPath, StandardOpenOption.READ);
                    waypointsChannel.lock(0, Long.MAX_VALUE, true);
                    waypointsModified = Files.getLastModifiedTime(waypointsPath).toMillis();
                    waypointsSize = waypointsChannel.size();
                    buffer = MapUtils.readFileToBuffer(waypointsChannel, buffer, waypointsSize);
                    buffer.flip();
                    try {
                        waypointsChannel.close();
                    } catch (IOException ex) {
                        ShadowMap.getLogger().error("Couldn't load waypoints for world " + worldKey, ex);
                        // TODO mark loaded waypoints as errored, prevent file overwrite after corrupted load
                    }
                    if (buffer.hasRemaining()) {
                        NbtCompound worldNbt = MapUtils.readCompressedNbt(new ByteBufferInputStream(buffer));
                        if (worldNbt.contains("waypoints", NbtElement.COMPOUND_TYPE)) {
                            waypointManager.loadNbt(worldNbt.getCompound("waypoints"));
                        }
                        waypointManager.setSaved(waypointsModified);
                    }
                }
            } catch (IOException ex) {
                ShadowMap.getLogger().error("Couldn't load waypoints for world " + worldKey, ex);
                // TODO mark loaded waypoints as errored, prevent file overwrite after corrupted load
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                try {
                    if (waypointsChannel != null) {
                        waypointsChannel.close();
                    }
                } catch (IOException ex) {
                    ShadowMap.getLogger().error("Couldn't load waypoints for world " + worldKey, ex);
                    // TODO mark loaded waypoints as errored, prevent file overwrite after corrupted load
                }
                if (buffer != null) {
                    mapManager.getIOBufferPool().release(buffer);
                }
            }
            return null;
        }
    }

    private class WaypointSaveTask implements Callable<Void> {
        public WaypointSaveTask() {}

        @Override
        public Void call() {
            ByteBuffer buffer = null;
            FileChannel waypointsChannel = null;

            try {
                buffer = mapManager.getIOBufferPool().take();
                Path waypointsPath = worldDirectory.resolve("world.dat");
                boolean waypointsExists = Files.exists(waypointsPath);
                long waypointsModified = 0;
                long waypointsSize = 0;

                Files.createDirectories(waypointsPath.getParent());
                waypointsChannel = FileChannel.open(waypointsPath, StandardOpenOption.CREATE, StandardOpenOption.READ,
                        StandardOpenOption.WRITE, StandardOpenOption.SYNC);
                waypointsChannel.lock(0, Long.MAX_VALUE, false);

                if (waypointManager.getSaved() < waypointsModified) {
                    waypointsModified = Files.getLastModifiedTime(waypointsPath).toMillis();
                    waypointsSize = waypointsChannel.size();
                    buffer = MapUtils.readFileToBuffer(waypointsChannel, buffer, waypointsSize);
                    buffer.flip();
                    if (buffer.hasRemaining()) {
                        NbtCompound worldNbt = MapUtils.readCompressedNbt(new ByteBufferInputStream(buffer));
                        if (worldNbt.contains("waypoints", NbtElement.COMPOUND_TYPE)) {
                            waypointManager.loadNbt(worldNbt.getCompound("waypoints"));
                        }
                        waypointManager.setSaved(waypointsModified);
                    }
                    buffer.clear();
                }

                NbtCompound waypointsNbt = waypointManager.toNbt();
                NbtCompound root = new NbtCompound();
                root.put("waypoints", waypointsNbt);

                ByteBufferOutputStream bufferOutput = new ByteBufferOutputStream(buffer);
                MapUtils.writeCompressedNbt("world", root, bufferOutput);
                buffer = bufferOutput.getBuffer();
                buffer.flip();
                MapUtils.writeFileFromBuffer(waypointsChannel, buffer);
                waypointsModified = Files.getLastModifiedTime(waypointsPath).toMillis();
                waypointManager.setSaved(waypointsModified);
            } catch (IOException ex) {
                ShadowMap.getLogger().error("Couldn't load waypoints for world " + worldKey, ex);
                // TODO mark loaded waypoints as errored, prevent file overwrite after corrupted load
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                try {
                    if (waypointsChannel != null) {
                        waypointsChannel.close();
                    }
                } catch (IOException ex) {
                    ShadowMap.getLogger().error("Couldn't load waypoints for world " + worldKey, ex);
                    // TODO mark loaded waypoints as errored, prevent file overwrite after corrupted load
                }
                if (buffer != null) {
                    mapManager.getIOBufferPool().release(buffer);
                }
            }
            return null;
        }
    }

    private class RegionLoadTask implements Callable<Void> {
        private final int regionX, regionZ;

        public RegionLoadTask(int regionX, int regionZ) {
            this.regionX = regionX;
            this.regionZ = regionZ;
        }

        @Override
        public Void call() {
            if (mapManager.isShuttingDown()) {
                return null;
            }
            RegionContainerImpl region = getRegion(regionX, regionZ, true, false);
            String name = getRegionFileName(region.getRegionX(), region.getRegionZ());
            ByteBuffer[] buffers = new ByteBuffer[2];
            FileChannel blocksChannel = null;
            FileChannel metaChannel = null;
            MergeResult mergeResult = MergeResult.getResult();

            try {
                mapManager.getIOBufferPool().bulkTake(buffers);

                Path blocksPath = worldDirectory.resolve("chunks/" + name);
                Path metaPath = worldDirectory.resolve("meta/" + name);
                boolean blocksExists = Files.exists(blocksPath);
                boolean metaExists = Files.exists(metaPath);
                long blocksModified = 0;
                long metaModified = 0;
                long blocksSize = 0;
                long metaSize = 0;

                // Check files, get channels, acquire locks, etc.
                if (blocksExists) {
                    blocksChannel = FileChannel.open(blocksPath, StandardOpenOption.READ);
                    blocksChannel.lock(0, Long.MAX_VALUE, true);
                    blocksModified = Files.getLastModifiedTime(blocksPath).toMillis();
                    blocksSize = blocksChannel.size();
                }
                if (metaExists) {
                    metaChannel = FileChannel.open(metaPath, StandardOpenOption.READ);
                    metaChannel.lock(0, Long.MAX_VALUE, true);
                    metaModified = Files.getLastModifiedTime(metaPath).toMillis();
                    metaSize = metaChannel.size();
                }

                // Once locks are acquired, read files to buffer and close
                // channel to minimize read and lock times.
                if (blocksExists) {
                    buffers[0] = MapUtils.readFileToBuffer(blocksChannel, buffers[0], blocksSize);
                    buffers[0].flip();
                    try {
                        blocksChannel.close();
                    } catch (IOException ex) {
                        ShadowMap.getLogger().error("Couldn't load file for region " + regionX + " " + regionZ, ex);
                        region.setFlag(RegionFlags.IO_FAILED);
                    }
                }
                if (metaExists) {
                    buffers[1] = MapUtils.readFileToBuffer(metaChannel, buffers[1], metaSize);
                    buffers[1].flip();
                    try {
                        metaChannel.close();
                    } catch (IOException ex) {
                        ShadowMap.getLogger().error("Couldn't load file for region " + regionX + " " + regionZ, ex);
                        region.setFlag(RegionFlags.IO_FAILED);
                    }
                }

                // Decompress and parse buffer contents, merge into loaded.
                if (blocksExists && buffers[0].hasRemaining()) {
                    NbtCompound blocksNbt = MapUtils.readCompressedNbt(new ByteBufferInputStream(buffers[0]));
                    BlocksRegion newBlocks = new BlocksRegion(region);
                    newBlocks.loadFromNbt(blocksNbt);
                    newBlocks.setLastSaved(blocksModified);
                    BlocksRegion oldBlocks = region.getOrUseBlocks(newBlocks);
                    if (oldBlocks == newBlocks) {
                        mergeResult = mergeResult.usedOther().renderNeeded();
                    } else {
                        mergeResult = mergeResult.includeResult(oldBlocks.mergeFrom(newBlocks));
                    }
                }
                if (metaExists && buffers[1].hasRemaining()) {
                    NbtCompound metaNbt = MapUtils.readCompressedNbt(new ByteBufferInputStream(buffers[1]));
                    ApiUser<StorageKeyImpl<?, ?, ?>>[] storageKeys = mapManager.getStorageKeys();
                    for (int i = 0; i < storageKeys.length; i++) {
                        ApiUser<StorageKeyImpl<?, ?, ?>> key = storageKeys[i];
                        NbtCompound storageNbt = metaNbt.contains(key.mod.modId, NbtElement.COMPOUND_TYPE)
                                ? metaNbt.getCompound(key.mod.modId) : null;
                        mergeResult = mergeResult.includeResult(loadStorage(key.user, region, storageNbt, metaModified));
                        metaNbt.remove(key.mod.modId);
                    }
                    region.setRetainedMeta(metaNbt);
                }
            } catch (IOException | CrashException ex) {
                ShadowMap.getLogger().error("Couldn't load file for region " + regionX + " " + regionZ, ex);
                region.setFlag(RegionFlags.IO_FAILED);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                try {
                    if (blocksChannel != null) {
                        blocksChannel.close();
                    }
                } catch (IOException ex) {
                    ShadowMap.getLogger().error("Couldn't load file for region " + regionX + " " + regionZ, ex);
                    region.setFlag(RegionFlags.IO_FAILED);
                }
                try {
                    if (metaChannel != null) {
                        metaChannel.close();
                    }
                } catch (IOException ex) {
                    ShadowMap.getLogger().error("Couldn't load file for region " + regionX + " " + regionZ, ex);
                    region.setFlag(RegionFlags.IO_FAILED);
                }

                mapManager.getIOBufferPool().bulkRelease(buffers);
                region.clearFlag(RegionFlags.LOAD_NEEDED);

                if (mergeResult.isRenderNeeded() || mergeResult.isUsedOther()) {
                    rerenderSurrounding(region);
                }
            }
            return null;
        }
    }

    private class RegionSaveTask implements Callable<Void> {
        private final RegionContainerImpl region;
        private final CompletableFuture<Integer> completeOnFinish;

        public RegionSaveTask(RegionContainerImpl region, CompletableFuture<Integer> completeOnFinish) {
            this.region = region;
            this.completeOnFinish = completeOnFinish;
        }

        @Override
        public Void call() {
            region.processUpdates();
            String name = getRegionFileName(region.getRegionX(), region.getRegionZ());
            ByteBuffer[] buffers = new ByteBuffer[2];

            FileChannel blocksChannel = null;
            FileChannel metaChannel = null;
            MergeResult mergeResult = MergeResult.getResult();

            int success = 0;
            try {
                mapManager.getIOBufferPool().bulkTake(buffers);

                Path blocksPath = worldDirectory.resolve("chunks/" + name);
                Path metaPath = worldDirectory.resolve("meta/" + name);
                Files.createDirectories(blocksPath.getParent());
                Files.createDirectories(metaPath.getParent());
                long blocksModified = 0;
                long metaModified = 0;
                long blocksSize = 0;
                long metaSize = 0;

                // Create files and channels, acquire locks, etc.
                blocksChannel = FileChannel.open(blocksPath, StandardOpenOption.CREATE, StandardOpenOption.READ,
                        StandardOpenOption.WRITE, StandardOpenOption.SYNC);
                blocksChannel.lock(0, Long.MAX_VALUE, false);
                blocksModified = Files.getLastModifiedTime(blocksPath).toMillis();
                blocksSize = blocksChannel.size();

                metaChannel = FileChannel.open(metaPath, StandardOpenOption.CREATE, StandardOpenOption.READ,
                        StandardOpenOption.WRITE, StandardOpenOption.SYNC);
                metaChannel.lock(0, Long.MAX_VALUE, false);
                metaModified = Files.getLastModifiedTime(metaPath).toMillis();
                metaSize = metaChannel.size();

                // If files on disk have been modified since they were last
                // loaded, load and merge before overwriting. This must be
                // atomic with respect to the files on disk and in memory, so
                // not much room for speeding up besides working off RAM buffer.
                BlocksRegion loadedBlocks = region.getBlocks();
                if (loadedBlocks != null && blocksModified > loadedBlocks.getLastSaved() && blocksSize > 0) {
                    ByteBuffer buffer = buffers[0] = MapUtils.readFileToBuffer(blocksChannel, buffers[0], blocksSize);
                    buffer.flip();
                    NbtCompound blocksNbt = MapUtils.readCompressedNbt(new ByteBufferInputStream(buffer));
                    BlocksRegion newBlocks = new BlocksRegion(region);
                    newBlocks.loadFromNbt(blocksNbt);
                    newBlocks.setLastSaved(blocksModified);
                    mergeResult = mergeResult.includeResult(loadedBlocks.mergeFrom(newBlocks));
                    buffer.clear();
                }
                ApiUser<StorageKeyImpl<?, ?, ?>>[] storageKeys = mapManager.getStorageKeys();
                long oldestMetaSave = Long.MAX_VALUE;
                long newestMetaModified = 0;
                for (int i = 0; i < storageKeys.length && oldestMetaSave > metaModified; i++) {
                    ApiUser<StorageKeyImpl<?, ?, ?>> key = storageKeys[i];
                    MapRegion<?, ?> loadedMeta = region.getStorage(key.user);
                    if (loadedMeta != null) {
                        oldestMetaSave = Math.min(oldestMetaSave, loadedMeta.getLastSaved());
                        newestMetaModified = Math.max(newestMetaModified, loadedMeta.getLastModified());
                    }
                }
                if (metaModified > oldestMetaSave && metaSize > 0) {
                    ByteBuffer buffer = buffers[1] = MapUtils.readFileToBuffer(metaChannel, buffers[1], metaSize);
                    buffer.flip();
                    NbtCompound metaNbt = MapUtils.readCompressedNbt(new ByteBufferInputStream(buffer));
                    for (int i = 0; i < storageKeys.length; i++) {
                        ApiUser<StorageKeyImpl<?, ?, ?>> key = storageKeys[i];
                        NbtCompound storageNbt = metaNbt.contains(key.mod.modId, NbtElement.COMPOUND_TYPE)
                                ? metaNbt.getCompound(key.mod.modId) : null;
                        mergeResult = mergeResult.includeResult(loadStorage(key.user, region, storageNbt, metaModified));
                        metaNbt.remove(key.mod.modId);
                    }
                    region.setRetainedMeta(metaNbt);
                    buffer.clear();
                    oldestMetaSave = Long.MAX_VALUE;
                    newestMetaModified = 0;
                    for (int i = 0; i < storageKeys.length && oldestMetaSave > metaModified; i++) {
                        ApiUser<StorageKeyImpl<?, ?, ?>> key = storageKeys[i];
                        MapRegion<?, ?> loadedMeta = region.getStorage(key.user);
                        if (loadedMeta != null) {
                            oldestMetaSave = Math.min(oldestMetaSave, loadedMeta.getLastSaved());
                            newestMetaModified = Math.max(newestMetaModified, loadedMeta.getLastModified());
                        }
                    }
                }

                // Once region in memory is known to be the latest available,
                // save to buffer to ensure files don't get corrupted by a
                // partial save that throws an exception.
                if (loadedBlocks != null && loadedBlocks.isModified()) {
                    NbtCompound blocksNbt = loadedBlocks.saveToNbt();
                    ByteBufferOutputStream bufferOutput = new ByteBufferOutputStream(buffers[0]);
                    MapUtils.writeCompressedNbt("region", blocksNbt, bufferOutput);
                    buffers[0] = bufferOutput.getBuffer();
                }
                if (oldestMetaSave < newestMetaModified) {
                    NbtCompound metaNbt = region.getRetainedMeta();
                    if (metaNbt == null) {
                        metaNbt = new NbtCompound();
                    }
                    for (int i = 0; i < storageKeys.length; i++) {
                        ApiUser<StorageKeyImpl<?, ?, ?>> key = storageKeys[i];
                        MapRegion<?, ?> loadedMeta = region.getStorage(key.user);
                        if (loadedMeta != null) {
                            NbtCompound storageNbt = loadedMeta.saveToNbt();
                            if (storageNbt != null && !storageNbt.isEmpty()) {
                                metaNbt.put(key.mod.modId, storageNbt);
                            }
                        }
                    }
                    if (!metaNbt.isEmpty()) {
                        ByteBufferOutputStream bufferOutput = new ByteBufferOutputStream(buffers[1]);
                        MapUtils.writeCompressedNbt("rmeta", metaNbt, bufferOutput);
                        buffers[1] = bufferOutput.getBuffer();
                    }
                }

                // Only once buffers have successfully been saved to, write out
                // their contents to the files.
                ByteBuffer buffer = buffers[0].flip();
                if (loadedBlocks != null && buffer.hasRemaining()) {
                    MapUtils.writeFileFromBuffer(blocksChannel, buffer);
                    loadedBlocks.setLastSaved(Files.getLastModifiedTime(blocksPath).toMillis());
                }
                buffer = buffers[1].flip();
                if (buffer.hasRemaining()) {
                    MapUtils.writeFileFromBuffer(metaChannel, buffer);
                    metaModified = Files.getLastModifiedTime(metaPath).toMillis();
                    for (int i = 0; i < storageKeys.length; i++) {
                        ApiUser<StorageKeyImpl<?, ?, ?>> key = storageKeys[i];
                        MapRegion<?, ?> loadedMeta = region.getStorage(key.user);
                        if (loadedMeta != null) {
                            loadedMeta.setLastSaved(metaModified);
                        }
                    }
                }
                region.clearFlag(RegionFlags.FORCE_SAVE);
                success = 1;
            } catch (IOException | CrashException ex) {
                ShadowMap.getLogger().error("Couldn't save file for region " + region.getRegionX() + " " + region.getRegionZ(), ex);
                region.setFlag(RegionFlags.IO_FAILED);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                region.clearFlag(RegionFlags.SAVE_SCHEDULED);

                try {
                    if (blocksChannel != null) {
                        blocksChannel.close();
                    }
                } catch (IOException ex) {
                    ShadowMap.getLogger().error("Couldn't save file for region " + region.getRegionX() + " " + region.getRegionZ(),
                            ex);
                    region.setFlag(RegionFlags.IO_FAILED);
                }
                try {
                    if (metaChannel != null) {
                        metaChannel.close();
                    }
                } catch (IOException ex) {
                    ShadowMap.getLogger().error("Couldn't save file for region " + region.getRegionX() + " " + region.getRegionZ(),
                            ex);
                    region.setFlag(RegionFlags.IO_FAILED);
                }

                mapManager.getIOBufferPool().bulkRelease(buffers);

                if (mergeResult.isRenderNeeded() || mergeResult.isUsedOther()) {
                    rerenderSurrounding(region);
                }

                if (completeOnFinish != null) {
                    completeOnFinish.complete(success);
                }
            }
            return null;
        }
    }
}
