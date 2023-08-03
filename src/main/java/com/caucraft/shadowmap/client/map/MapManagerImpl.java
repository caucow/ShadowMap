package com.caucraft.shadowmap.client.map;

import com.caucraft.shadowmap.api.map.MapManager;
import com.caucraft.shadowmap.api.map.RegionFlags;
import com.caucraft.shadowmap.api.util.ChunkCache;
import com.caucraft.shadowmap.api.util.RenderArea;
import com.caucraft.shadowmap.api.util.ServerKey;
import com.caucraft.shadowmap.api.util.WorldKey;
import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.config.MinimapConfig;
import com.caucraft.shadowmap.client.config.PerformanceConfig;
import com.caucraft.shadowmap.client.importer.ImportManager;
import com.caucraft.shadowmap.client.util.ApiUser;
import com.caucraft.shadowmap.client.util.MapBlockStateMutable;
import com.caucraft.shadowmap.client.util.MapUtils;
import com.caucraft.shadowmap.client.util.data.ResourcePool;
import com.caucraft.shadowmap.client.util.task.CleanupCounter;
import com.caucraft.shadowmap.client.util.task.CleanupHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Striped;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Main management class and root container of map data. This contains the
 * current active world, caches recently loaded worlds, and manages the IO and
 * task threads for loading/saving and rendering the map.
 */
public class MapManagerImpl implements MapManager, Closeable, SimpleSynchronousResourceReloadListener {

    public static final Identifier RESOURCE_ID = new Identifier(ShadowMap.MOD_ID, "map_manager");

    private final ShadowMap shadowMap;
    private final File mapsDirectory;
    private final AtomicBoolean shutdown;
    private final ShutdownPhaser shutdownPhaser;
    private final ImportManager importManager;
    private final ScheduledExecutorService delayedExecutor;
    private final ScheduledExecutorService ioExecutor;
    private final ScheduledExecutorService modifyExecutor;
    private final ScheduledExecutorService renderExecutor;
    private final Striped<ReadWriteLock> regionLocks;
    private final ReadWriteLock globalLock;
    private final Object2ObjectLinkedOpenHashMap<WorldKey, MapWorldImpl> loadedWorlds;
    private final ResourcePool<ByteBuffer> ioBufferPool;
    private final ResourcePool<int[]> renderBufferPool;
    private final PriorityBlockingQueue<PriorityContainer<Void>> ioQueue;
    private final PriorityBlockingQueue<PriorityContainer<Void>> renderQueue;
    private final ScheduledFuture<?> cleanupFuture;
    private volatile ScheduledFuture<?> saveScanFuture;
    private ApiUser<StorageKeyImpl<?, ?, ?>>[] storageKeys;

    private MapWorldImpl currentWorldMap;
    long lastTickTime;

    public MapManagerImpl(ShadowMap shadowMap, File mapsDirectory) {
        this.shadowMap = shadowMap;
        this.mapsDirectory = mapsDirectory;
        this.shutdown = new AtomicBoolean();
        this.shutdownPhaser = new ShutdownPhaser();
        AtomicInteger threadCounter = new AtomicInteger();
        this.delayedExecutor = Executors.newScheduledThreadPool(1, (runnable) -> {
            Thread t = new Thread(runnable, "SM-MapDelayScheduler-" + threadCounter.incrementAndGet());
            t.setUncaughtExceptionHandler((thread, ex) -> ShadowMap.getLogger().error("Uncaught exception in " + thread.getName(), ex));
            t.setDaemon(true);
            return t;
        });
        this.ioExecutor = Executors.newScheduledThreadPool(2, (runnable) -> {
            Thread t = new Thread(runnable, "SM-MapIOThread-" + threadCounter.incrementAndGet());
            t.setUncaughtExceptionHandler((thread, ex) -> ShadowMap.getLogger().error("Uncaught exception in " + thread.getName(), ex));
            t.setDaemon(true);
            return t;
        });
        this.modifyExecutor = Executors.newScheduledThreadPool(2, (runnable) -> {
            Thread t = new Thread(runnable, "SM-MapUpdateThread-" + threadCounter.incrementAndGet());
            t.setUncaughtExceptionHandler((thread, ex) -> ShadowMap.getLogger().error("Uncaught exception in " + thread.getName(), ex));
            t.setDaemon(true);
            return t;
        }); // TODO make pool size configgable
        this.renderExecutor = Executors.newScheduledThreadPool(2, (runnable) -> {
            Thread t = new Thread(runnable, "SM-MapRenderThread-" + threadCounter.incrementAndGet());
            t.setUncaughtExceptionHandler((thread, ex) -> ShadowMap.getLogger().error("Uncaught exception in " + thread.getName(), ex));
            t.setDaemon(true);
            return t;
        }); // TODO make pool size configgable
        this.regionLocks = Striped.readWriteLock(1024);
        this.globalLock = new ReentrantReadWriteLock();
        this.loadedWorlds = new Object2ObjectLinkedOpenHashMap<>(8);
        this.ioBufferPool = new ResourcePool<>(
                () -> ByteBuffer.allocate(MapUtils.DEFAULT_BUFFER_SIZE),
                ByteBuffer::clear, 4, 12);
        this.renderBufferPool = new ResourcePool<>(() -> new int[512 * 512], (tess) -> {}, 16, 32);
        this.ioQueue = new PriorityBlockingQueue<>();
        this.renderQueue = new PriorityBlockingQueue<>();

        this.importManager = new ImportManager(this);
        scheduleSaveScan();
        cleanupFuture = this.modifyExecutor.scheduleWithFixedDelay(new ErrorReportingTask<>(this::cleanupRegions), 15, 15, TimeUnit.SECONDS);
    }

    public void setStorageKeys(ApiUser<StorageKeyImpl<?, ?, ?>>[] storageKeys) {
        if (this.storageKeys != null) {
            throw new IllegalStateException("Storage keys have already been set.");
        }
        this.storageKeys = storageKeys;
    }

    /**
     * Shuts down executor threads, waiting first for the executors to finish
     * their tasks, and saves all loaded worlds.
     */
    @Override
    public void close() {
        // Ignore remaining tasks, map is shutting down.
        shutdown.set(true);
        saveScanFuture.cancel(false);
        cleanupFuture.cancel(false);
        ShadowMap.getLogger().info("Shutting down map");

        importManager.close();
        List<Runnable> remainingTasks = delayedExecutor.shutdownNow();
        for (int i = remainingTasks.size(); i > 0; i--) {
            shutdownPhaser.arriveAndDeregister();
        }
        remainingTasks = renderExecutor.shutdownNow();
        for (int i = remainingTasks.size(); i > 0; i--) {
            shutdownPhaser.arriveAndDeregister();
        }
        if (shutdownPhaser.register() >= 0) {
            int phase = shutdownPhaser.arriveAndDeregister();
            try {
                shutdownPhaser.awaitAdvanceInterruptibly(phase, 5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            } catch (TimeoutException ex) {
                ShadowMap.getLogger().warn("Timed out waiting for tasks to finish before shutdown. Some data may be lost.");
            }
        }
        modifyExecutor.shutdown();
        try {
            if (!modifyExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ShadowMap.getLogger().warn("Region modify executor took too long to shut down.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            ShadowMap.getLogger().warn("Region modify executor shutdown interrupted.");
            modifyExecutor.shutdownNow();
        }
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ShadowMap.getLogger().warn("IO Executor shutdown took too long to shut down. Some regions may not have been saved.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            ShadowMap.getLogger().warn("IO executor shutdown interrupted, some changes may have been lost.");
            ioExecutor.shutdownNow();
        }
        synchronized (this) {
            List<RegionContainerImpl> regionList = new ArrayList<>();
            for (MapWorldImpl world : loadedWorlds.values()) {
                if (world.getWaypointManager().isModified()) {
                    world.scheduleWaypointSave();
                }
                regionList.addAll(world.getRegions());
            }
            ShadowMap.getLogger().info("Checking and saving up to " + regionList.size() + " regions");
            scheduleRegionsForSave(regionList, false);
        }
        ShadowMap.getLogger().info("Map shut down");
    }

    @Override
    public Identifier getFabricId() {
        return RESOURCE_ID;
    }

    public boolean isShuttingDown() {
        return shutdown.get();
    }

    @Override
    public void reload(ResourceManager manager) {
        MinecraftClient mcClient = MinecraftClient.getInstance();
        TextureManager textureManager = mcClient.getTextureManager();
        BlockRenderManager blockRenderer = mcClient.getBlockRenderManager();
        World world = mcClient.world;
        Iterator<BlockState> stateIterator;

        if (world == null) {
            stateIterator = Registries.BLOCK
                    .stream()
                    .flatMap((block) -> block.getStateManager().getStates().stream())
                    .iterator();
        } else {
            stateIterator = world.getRegistryManager()
                    .get(RegistryKeys.BLOCK)
                    .stream()
                    .flatMap((block) -> block.getStateManager().getStates().stream())
                    .iterator();
        }

        Random random = Random.create(0);
        Map<Identifier, BufferedImage> loadedAtlasMap = new HashMap<>();

        while (stateIterator.hasNext()) {
            BlockState state = stateIterator.next();
            MapBlockStateMutable mapData = (MapBlockStateMutable) state;
            Sprite stateSprite;
            boolean hasTint = false;
            if (state.getRenderType() != BlockRenderType.MODEL) {
                stateSprite = blockRenderer.getModels().getModelParticleSprite(state);
            } else {
                List<BakedQuad> quadList = blockRenderer.getModel(state).getQuads(state, Direction.UP, random);
                if (quadList.isEmpty()) {
                    quadList = blockRenderer.getModel(state).getQuads(state, null, random);
                }
                if (quadList.isEmpty()) {
                    stateSprite = blockRenderer.getModels().getModelParticleSprite(state);
                } else {
                    BakedQuad topSurface = quadList.get(0);
                    stateSprite = topSurface.getSprite();
                    hasTint = topSurface.hasColor();
                }
            }
            SpriteContents stateSpriteContents = stateSprite.getContents();
            BufferedImage texAtlas = loadTextureAtlas(textureManager, loadedAtlasMap, stateSprite.getAtlasId());

            int minx = stateSprite.getX();
            int miny = stateSprite.getY();
            int maxx = minx + stateSpriteContents.getWidth();
            int maxy = miny + stateSpriteContents.getHeight();
            int minTexX = maxx;
            int minTexY = maxy;
            int maxTexX = minx;
            int maxTexY = miny;
            int r = 0;
            int g = 0;
            int b = 0;
            int a = 0;
            int count = 0;
            for (int x = minx; x < maxx; x++) {
                for (int y = miny; y < maxy; y++) {
                    int argb = texAtlas.getRGB(x, y);
                    int nextA = (argb >>> 24 & 0xFF);
                    if (nextA > 8) {
                        a += nextA;
                        r += (argb >>> 16 & 0xFF);
                        g += (argb >>> 8 & 0xFF);
                        b += (argb & 0xFF);
                        count++;
                        minTexX = Math.min(minTexX, x);
                        minTexY = Math.min(minTexY, y);
                        maxTexX = Math.max(maxTexX, x);
                        maxTexY = Math.max(maxTexY, y);
                    }
                }
            }

            MapUtils.updateOpacity(state);
            mapData.shadowMap$setTinted(hasTint);
            if (minTexX <= maxTexX & minTexY <= maxTexY) {
                r /= count;
                g /= count;
                b /= count;
                a /= (maxTexX - minTexX + 1) * (maxTexY - minTexY + 1);
                if (r > 255 | g > 255 | b > 255 | a > 255) {
                    ShadowMap.getLogger().error(String.format("Invalid ARGB for block %s: %d %d %d %d (c: %d, tex: %d %d %d %d)", state, a, r, g, b, count, minTexX, minTexY, maxTexX, maxTexY));
                }
                a = Math.min(a, mapData.shadowMap$getMaxOpacity());
                mapData.shadowMap$setColorARGB(a << 24 | r << 16 | g << 8 | b);
            } else {
                mapData.shadowMap$setColorARGB(0);
            }
        }
        MapBlockStateMutable mapData = (MapBlockStateMutable) Blocks.AIR.getDefaultState();
        mapData.shadowMap$setColorARGB(0);
        mapData.shadowMap$setTinted(false);
        mapData.shadowMap$setOpacity(false, 0);
        mapData = (MapBlockStateMutable) Blocks.CAVE_AIR.getDefaultState();
        mapData.shadowMap$setColorARGB(0);
        mapData.shadowMap$setTinted(false);
        mapData.shadowMap$setOpacity(false, 0);
        mapData = (MapBlockStateMutable) Blocks.VOID_AIR.getDefaultState();
        mapData.shadowMap$setColorARGB(0);
        mapData.shadowMap$setTinted(false);
        mapData.shadowMap$setOpacity(false, 0);
        mapData = (MapBlockStateMutable) Blocks.BARRIER.getDefaultState();
        mapData.shadowMap$setColorARGB(0x40ff0000);
        mapData.shadowMap$setTinted(false);
        mapData.shadowMap$setOpacity(false, 64);

        synchronized (this) {
            for (MapWorldImpl loadedWorld : new ArrayList<>(loadedWorlds.values())) {
                for (RegionContainerImpl loadedRegion : loadedWorld.getRegions()) {
                    loadedRegion.scheduleRerenderAll(true);
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // <editor-fold desc="Getter/Setter (thread safe)">

    public ApiUser<StorageKeyImpl<?, ?, ?>>[] getStorageKeys() {
        return storageKeys;
    }

    @Override
    public MapWorldImpl getCurrentWorld() {
        return currentWorldMap;
    }

    public ImportManager getImportManager() {
        return importManager;
    }

    public File getMapsDirectory() {
        return mapsDirectory;
    }

    @Override
    public MapWorldImpl getWorld(WorldKey key) {
        return loadedWorlds.get(key);
    }

    /**
     * Gets the ReadWriteLock for a given region from the MapManagerImpl's
     * {@link Striped}. Internally, the Striped is sized with 1024 locks, and
     * the low 5 bits of the region's X and Z coordinate are combined to get the
     * index of the lock in the Striped.
     * @param regionX x coordinate of the region to get the lock for
     * @param regionZ z coordinate of the region to get the lock for
     * @return the lock corresponding to this region
     */
    public ReadWriteLock getRegionLock(int regionX, int regionZ) {
        return regionLocks.getAt((regionZ & 0x1F) << 5 | (regionX & 0x1F));
    }

    public ReadWriteLock getGlobalLock() {
        return globalLock;
    }

    public ResourcePool<ByteBuffer> getIOBufferPool() {
        return this.ioBufferPool;
    }

    public ResourcePool<int[]> getRenderBufferPool() {
        return this.renderBufferPool;
    }

    /**
     * Changes the current world loaded by the map manager. This is a shortcut
     * for {@link #onWorldChanged(WorldKey, World, Registry, Registry)}.
     * @param world the world being switched to, or null if the world has been
     * unloaded.
     */
    public synchronized void onWorldChanged(World world) {
        if (world == null) {
            closeWorld(currentWorldMap);
            return;
        }

        // Basically copied from MinecraftClient.getWindowTitle()
        // TODO figure out how to get server details for REALMS
        // Realms: RealmsMainScreen

        ServerKey serverKey = ShadowMap.getInstance().getServerKey();
        if (serverKey == null) {
            ShadowMap.getLogger().warn("Server key is null, current server connection method is unsupported (realms?). Map will not be loaded.");
            return;
        }
        Identifier worldId = world.getRegistryKey().getValue();
        Identifier dimId = world.getDimensionKey().getValue();
        WorldKey worldKey = WorldKey.newKey(
                serverKey,
                worldId.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? worldId.getPath() : worldId.toString(),
                dimId.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? dimId.getPath() : dimId.toString()
        );
        DynamicRegistryManager registryManager = world.getRegistryManager();
        Registry<Block> blockRegistry = registryManager.get(RegistryKeys.BLOCK);
        Registry<Biome> biomeRegistry = registryManager.get(RegistryKeys.BIOME);
        onWorldChanged(worldKey, world, blockRegistry, biomeRegistry);
    }

    private synchronized void onWorldChanged(WorldKey worldKey, World world, Registry<Block> blockRegistry, Registry<Biome> biomeRegistry) {
        if (currentWorldMap != null) {
            if (worldKey.equals(currentWorldMap.getWorldKey())) {
                currentWorldMap.updateWorldAndRegistries(world, blockRegistry, biomeRegistry);
                return;
            } else {
                closeWorld(currentWorldMap);
            }
        }
        ShadowMap.getLogger().info("Loading world " + worldKey);
        MapWorldImpl newMapWorld = loadedWorlds.get(worldKey);
        if (newMapWorld == null) {
            newMapWorld = new MapWorldImpl(this, worldKey, mapsDirectory, world, blockRegistry, biomeRegistry);
            newMapWorld.scheduleWaypointLoad();
            loadedWorlds.put(worldKey, newMapWorld);
        } else {
            newMapWorld.updateWorldAndRegistries(world, blockRegistry, biomeRegistry);
            newMapWorld.scheduleWaypointLoad();
            if (importManager.isDirty()) {
                importManager.scheduleSave();
            }
        }
        currentWorldMap = newMapWorld;
        importManager.onWorldChanged(worldKey);
    }

    // TODO make clear warnings about default datapacks and
    //      block/biome/dimension registries
    public synchronized MapWorldImpl loadWorld(WorldKey key) {
        MapWorldImpl world = loadedWorlds.get(key);
        if (world != null) {
            return world;
        }
        ShadowMap shadowMap = ShadowMap.getInstance();
        Registry<Block> blockRegistry = shadowMap.getDefaultBlockRegistry();
        Registry<Biome> biomeRegistry = shadowMap.getDefaultBiomeRegistry();
        world = new MapWorldImpl(this, key, mapsDirectory, null, blockRegistry, biomeRegistry);
        loadedWorlds.put(key, world);
        return world;
    }

    public synchronized void closeWorld(MapWorldImpl world) {
        if (world == null) {
            return;
        }
        WorldKey worldKey = world.getWorldKey();
        MapWorldImpl loadedWorld = loadedWorlds.get(worldKey);

        if (world != loadedWorld) {
            ShadowMap.getLogger().warn("Tried to close world with same key as an existing loaded world, but the two worlds are not the same: " + worldKey);
        }

        scheduleWorldForSaveAndClean(world);

        if (world == currentWorldMap) {
            currentWorldMap = null;
        }
        world.setRenderPriorityArea(null, null);
    }

    public void closeWorld(WorldKey worldKey) {
        closeWorld(loadedWorlds.get(worldKey));
    }

    public void setPlayerPosition(int blockX, int blockZ) {
        MapWorldImpl currentWorld = currentWorldMap;
        if (currentWorld != null) {
            currentWorld.setPlayerPosition(blockX, blockZ);
        }
    }

    public void setCameraPosition(MinimapConfig config, int blockX, int blockZ) {
        MapWorldImpl currentWorld = currentWorldMap;
        if (currentWorld != null) {
            currentWorld.setCameraPosition(config, blockX, blockZ);
        }
    }

    public void clearFullmapFocus() {
        MapWorldImpl currentWorld = currentWorldMap;
        if (currentWorld != null) {
            currentWorld.clearFullmapFocus();
        }
    }

    public void setLastTickTime(long time) {
        this.lastTickTime = time;
    }

    // </editor-fold>

    ////////////////////////////////////////////////////////////////////////////
    // <editor-fold desc="Scheduling Methods (thread safe, schedules locking task)">

    public void scheduleUpdateChunk(WorldChunk chunk) {
        World world = chunk.getWorld();
        MapWorldImpl currentMap = currentWorldMap;
        if (currentMap.getWorld() != world) {
            return;
        }

        currentMap.scheduleUpdateChunk(world, chunk, new ChunkCache(chunk), ShadowMap.getLastTickTimeS()); // TODO cache curTimes/sync to tick
    }

    public void scheduleUpdateSurroundedChunk(WorldChunk chunk) {
        World world = chunk.getWorld();
        MapWorldImpl currentMap = currentWorldMap;
        if (currentMap.getWorld() != world) {
            return;
        }

        currentMap.scheduleUpdateSurroundedChunk(world, chunk, new ChunkCache(chunk), ShadowMap.getLastTickTimeS());
    }

    public void scheduleUpdateBlock(World world, BlockPos pos, BlockState state) {
        MapWorldImpl currentMap = currentWorldMap;
        if (currentMap.getWorld() != world) {
            return;
        }

        Chunk chunk = world.getChunk(pos);
        currentMap.scheduleUpdateBlocks(world, chunk, new ChunkCache((WorldChunk) chunk), pos, state, ShadowMap.getLastTickTimeS()); // TODO cache curTimes/sync to tick
    }

    void scheduleResortPriority() {
        modifyExecutor.execute(new ErrorReportingTask<>(this::resortPriority));
    }

    private void scheduleWorldForSaveAndClean(MapWorldImpl world) {
        CompletableFuture<Integer> saveTaskFinished;
        synchronized (this) {
            world.setRenderPriorityArea(null, null);
            if (world.getWaypointManager().isModified()) {
                world.scheduleWaypointSave();
            }
            if (importManager.isDirty()) {
                importManager.scheduleSave();
            }
            saveTaskFinished = scheduleRegionsForSave(world.getRegions(), true);
        }
        saveTaskFinished.thenRun(() -> {
            synchronized (MapManagerImpl.this) {
                if (world == currentWorldMap) {
                    return;
                }
                if (!world.isEmpty()) {
                    return;
                }
                ShadowMap.getLogger().info("Removing empty world " + world.getWorldKey());
                loadedWorlds.remove(world.getWorldKey(), world);
            }
        });
    }

    private void scheduleSaveScan() {
        ScheduledFuture<?> oldFuture = saveScanFuture;
        if (shutdown.get()) {
            return;
        }
        ScheduledFuture<?> newFuture;
        try {
            newFuture = saveScanFuture = this.ioExecutor.schedule(new ErrorReportingTask<>(this::scanForWorldSave), 60, TimeUnit.SECONDS);
        } catch (RejectedExecutionException ex) {
            return;
        }
        if (oldFuture != null && oldFuture.isCancelled()) {
            newFuture.cancel(true);
        }
        if (shutdown.get()) {
            newFuture.cancel(true);
        }
    }

    /**
     * Schedules all regions in the list to be saved. If {@code clean} is true,
     * they will be scheduled to be cleaned and unloaded afterwards.
     * @param regions regions to save and/or clean.
     * @param cleanAndRemove set to true if regions should be cleaned and
     * removed.
     * @return a future that is completed when all regions have been fully
     * saved and cleaned. If there are no regions, a completed future will be
     * returned. The completion value will be the number of successfully saved
     * regions.
     */
    private CompletableFuture<Integer> scheduleRegionsForSave(List<RegionContainerImpl> regions, boolean cleanAndRemove) {
        int saved = 0;
        long scheduleStartTime = System.nanoTime();
        CompletableFuture<Integer> previousFuture = CompletableFuture.completedFuture(0);
        for (RegionContainerImpl region : regions) {
            if (region.isModified() && !region.isFlagsSet(RegionFlags.IO_FAILED.flag)) {
                saved++;
                CompletableFuture<Integer> completeOnFinish = new CompletableFuture<>();
                region.getWorld().scheduleRegionSave(region, completeOnFinish);
                if (previousFuture != null) {
                    previousFuture = completeOnFinish.thenCombine(previousFuture, Integer::sum);
                } else {
                    previousFuture = completeOnFinish;
                }
                if (cleanAndRemove) {
                    CompletableFuture<Void> completeOnClean = new CompletableFuture<>();
                    completeOnFinish.thenRun(() -> region.getWorld().scheduleRegionCleanup(region, null, completeOnClean));
                    previousFuture = completeOnClean.thenCombine(previousFuture, (ignore, pass) -> pass);
                }
            } else if (cleanAndRemove) {
                if (previousFuture == null) {
                    previousFuture = CompletableFuture.completedFuture(0);
                }
                CompletableFuture<Void> completeOnClean = new CompletableFuture<>();
                region.getWorld().scheduleRegionCleanup(region, null, completeOnClean);
                previousFuture = completeOnClean.thenCombine(previousFuture, (ignore, pass) -> pass);
            }
        }
        if (saved > 0) {
            ShadowMap.getLogger().info("Scheduled save for " + saved + " regions");
            int scheduledSaves = saved;
            previousFuture.thenAccept((successfulSaves) -> {
                long scheduleDiff = System.nanoTime() - scheduleStartTime;
                ShadowMap.getLogger().info("Successfully saved " + successfulSaves + " of " + scheduledSaves + " regions in " + (scheduleDiff / 1_000_000) + "." + (scheduleDiff / 1000 % 1000) + "ms");
            });
        } else {
            ShadowMap.getLogger().info("No regions to save");
        }
        return previousFuture;
    }

    /**
     * Schedules a task to run in 100ms on the render threads. If the map
     * manager is being shut down, this will ignore the task and return.
     * @param world the world corresponding to the task
     * @param regionX x coordinate of the region corresponding to the task.
     * @param regionZ z coordinate of the region corresponding to the task.
     * @param task the task to run.
     */
    CompletableFuture<Void> executeRenderTask(MapWorldImpl world, int regionX, int regionZ, Callable<Void> task) {
        if (shutdown.get() || shutdownPhaser.register() < 0) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            PriorityContainer<Void> prioContainer = new PriorityContainer<>(world, regionX, regionZ, task);
            PriorityLockingTask<Void> prioTask = new PriorityLockingTask<>(renderQueue, false);
            // Delay adding the region to the priority queue, this must be done
            // on a less contended thread; delayedExecutor is only meant to
            // schedule other tasks, while renderExecutor will likely be busy.
            delayedExecutor.schedule(new ErrorReportingTask<Void>(() -> {
                renderQueue.add(prioContainer);
                try {
                    renderExecutor.execute(prioTask);
                } catch (RejectedExecutionException ex) {
                    shutdownPhaser.arriveAndDeregister();
                }
                return null;
            }), 100, TimeUnit.MILLISECONDS);
            return prioTask;
        } catch (RejectedExecutionException ex) {
            shutdownPhaser.arriveAndDeregister();
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Schedules a task to run on the task threads. If the map manager is being
     * shut down and the task cannot be registered with the phaser, it will
     * execute immediately on the calling thread.
     * @param regionX x coordinate of the region corresponding to the task.
     * @param regionZ z coordinate of the region corresponding to the task.
     * @param task the task to run.
     */
    <T> CompletableFuture<T> executeModifyTask(int regionX, int regionZ, Callable<T> task) {
        Lock regionLock = getRegionLock(regionX, regionZ).writeLock();
        LockingTask<T> lockingTask = new LockingTask<>(regionLock, task);
        if (shutdownPhaser.register() < 0) {
            lockingTask.run();
            return lockingTask;
        }
        try {
            modifyExecutor.schedule(lockingTask, 25, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ex) {
            lockingTask.run();
        }
        return lockingTask;
    }

    /**
     * Schedules a task to run on the IO threads. If the map manager is being
     * shut down and the task cannot be registered with the phaser, it will
     * execute immediately on the calling thread.
     * @param world the world corresponding to the task.
     * @param regionX x coordinate of the region corresponding to the task.
     * @param regionZ z coordinate of the region corresponding to the task.
     * @param task the task to run.
     * @param sortPriority whether to sort this task by its region's priority.
     */
    CompletableFuture<Void> executeIOTask(MapWorldImpl world, int regionX, int regionZ, Callable<Void> task, boolean sortPriority) {
        if (sortPriority) {
            return executePrioritySortedIOTask(world, regionX, regionZ, task);
        } else {
            return executeUnsortedIOTask(regionX, regionZ, task);
        }
    }

    private <T> CompletableFuture<T> executeUnsortedIOTask(int regionX, int regionZ, Callable<T> task) {
        Lock regionLock = getRegionLock(regionX, regionZ).writeLock();
        LockingTask<T> lockingTask = new LockingTask<>(regionLock, task);
        if (shutdownPhaser.register() < 0) {
            lockingTask.run();
            return lockingTask;
        }
        try {
            ioExecutor.execute(lockingTask);
        } catch (RejectedExecutionException ex) {
            lockingTask.run();
        }
        return lockingTask;
    }

    private CompletableFuture<Void> executePrioritySortedIOTask(MapWorldImpl world, int regionX, int regionZ, Callable<Void> task) {
        PriorityLockingTask<Void> prioTask = new PriorityLockingTask<>(ioQueue, true);
        ioQueue.add(new PriorityContainer<>(world, regionX, regionZ, task));
        if (shutdownPhaser.register() < 0) {
            prioTask.run();
            return prioTask;
        }
        try {
            ioExecutor.execute(prioTask);
        } catch (RejectedExecutionException ex) {
            prioTask.run();
        }
        return prioTask;
    }

    /**
     * Schedules a task to run on the task threads. The task will use a "global"
     * lock independent of world regions. If the map manager is being shut down
     * and the task cannot be registered with the phaser, it will execute
     * immediately on the calling thread.
     * @param task the task to run.
     */
    public <T> CompletableFuture<T> executeGlobalModifyTask(Callable<T> task) {
        LockingTask<T> lockingTask = new LockingTask<>(globalLock.writeLock(), task);
        if (shutdownPhaser.register() < 0) {
            lockingTask.run();
            return lockingTask;
        }
        try {
            modifyExecutor.execute(lockingTask);
        } catch (RejectedExecutionException ex) {
            lockingTask.run();
        }
        return lockingTask;
    }

    /**
     * Schedules a task to run on the IO threads. The task will use a "global"
     * lock independent of world regions. If the map manager is being shut down
     * and the task cannot be registered with the phaser, it will execute
     * immediately on the calling thread.
     * @param task the task to run.
     */
    public <T> CompletableFuture<T> executeGlobalIOTask(Callable<T> task) {
        LockingTask<T> lockingTask = new LockingTask<>(globalLock.writeLock(), task);
        if (shutdownPhaser.register() < 0) {
            lockingTask.run();
            return lockingTask;
        }
        try {
            ioExecutor.execute(lockingTask);
        } catch (RejectedExecutionException ex) {
            lockingTask.run();
        }
        return lockingTask;
    }

    /**
     * Schedules a task to run on the IO threads without any locking mechanisms.
     * If the map manager is being shut down and the task cannot be registered
     * with the phaser, it will execute immediately on the calling thread.
     * @param task the task to run.
     * @return a CompletableFuture that is completed with null when the task
     * completes successfully, or with a Throwable if it fails.
     */
    public <T> CompletableFuture<T> executeNonLockingIOTask(Callable<T> task) {
        ErrorReportingTask<T> reportingTask = new ErrorReportingTask<>(task);
        if (shutdownPhaser.register() < 0) {
            reportingTask.run();
            return reportingTask;
        }
        reportingTask.whenComplete((val, ex) -> shutdownPhaser.arriveAndDeregister());
        try {
            ioExecutor.execute(reportingTask);
        } catch (RejectedExecutionException ex) {
            reportingTask.run();
        }
        return reportingTask;
    }

    // </editor-fold>

    ////////////////////////////////////////////////////////////////////////////
    // <editor-fold desc="Scheduled Modify/Update Methods">

    private Void resortPriority() {
        ArrayList<PriorityContainer<Void>> containers = new ArrayList<>(ioQueue.size() + 10);
        ioQueue.drainTo(containers);
        for (PriorityContainer<Void> container : containers) {
            int regionX = container.regionX;
            int regionZ = container.regionZ;
            long priority = container.world.getRenderPriority(regionX, regionZ);
            if (container.world != currentWorldMap) {
                priority <<= 8;
            }
            container.priority = priority;
            ioQueue.add(container);
        }

        containers.clear();
        containers.ensureCapacity(renderQueue.size() + 10);
        renderQueue.drainTo(containers);
        for (PriorityContainer<Void> container : containers) {
            int regionX = container.regionX;
            int regionZ = container.regionZ;
            long priority = container.world.getRenderPriority(regionX, regionZ);
            if (container.world != currentWorldMap) {
                priority <<= 8;
            }
            container.priority = priority;
            renderQueue.add(container);
        }
        return null;
    }

    private Void scanForWorldSave() {
        if (shutdown.get()) {
            return null;
        }
        if (importManager.isDirty()) {
            importManager.scheduleSave();
        }
        MapWorldImpl currentMap = currentWorldMap;
        if (currentMap == null) {
            scheduleSaveScan();
            return null;
        }
        if (currentMap.getWaypointManager().isModified()) {
            currentMap.scheduleWaypointSave();
        }
        List<RegionContainerImpl> regionList = currentMap.getRegions();
        CompletableFuture<Integer> saveFinishedFuture = scheduleRegionsForSave(regionList, false);
        saveFinishedFuture.whenComplete((val, ex) -> scheduleSaveScan());
        return null;
    }

    private Void cleanupRegions() {
        long curTime = ShadowMap.getLastTickTimeS();
        Set<CleanupSorter> cleanupRegions = new TreeSet<>();
        List<MapWorldImpl> worldList = ImmutableList.copyOf(loadedWorlds.values());
        synchronized (this) {
            int i = 0;
            for (MapWorldImpl world : worldList) {
                if (world.isEmpty() && world != currentWorldMap) {
                    ShadowMap.getLogger().info("Removing empty world " + world.getWorldKey());
                    loadedWorlds.remove(world.getWorldKey(), world);
                    continue;
                }
                RenderArea[] priorityArray = world.getRenderAreas();
                for (RegionContainerImpl region : world.getRegions()) {
                    int maxFlags = region.getMaxFlags();
                    long lastRead = region.getLastRead();
                    cleanupRegions.add(new CleanupSorter(i++, region, maxFlags, RenderArea.getRenderPriority(priorityArray, region.getRegionX(), region.getRegionZ()), lastRead));
                }
            }
        }
        if (cleanupRegions.size() <= 64) {
            return null;
        }
        PerformanceConfig config = shadowMap.getConfig().performanceConfig;
        CleanupCounter counter = new CleanupCounter();
        for (CleanupSorter sorter : cleanupRegions) {
            RegionContainerImpl region = sorter.region;
            CleanupHelper helper = region.isCleanupNeeded(counter, config, curTime);
            if (region.getFlags(~RegionFlags.IO_FAILED.flag) != 0) {
                continue;
            }
            if (helper.isAnyTrue() || region.isEmpty()) {
                region.getWorld().scheduleRegionCleanup(region, helper, null);
            }
            region.reduceMaxFlags();
        }
        return null;
    }

    // </editor-fold>

    private static BufferedImage loadTextureAtlas(TextureManager textureManager, Map<Identifier, BufferedImage> loadedMap, Identifier textureId) {
        BufferedImage texAtlas = loadedMap.get(textureId);
        if (texAtlas != null) {
            return texAtlas;
        }

        textureManager.bindTexture(textureId);
        int texWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int texHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        IntBuffer pixelBuffer = BufferUtils.createIntBuffer(texWidth * texHeight);
        int[] pixelArray = new int[texWidth * texHeight];
        texAtlas = new BufferedImage(texWidth, texHeight, BufferedImage.TYPE_INT_ARGB);
        loadedMap.put(textureId, texAtlas);

        pixelBuffer.position(0);

        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);
        pixelBuffer.get(pixelArray);
        texAtlas.setRGB(0, 0, texWidth, texHeight, pixelArray, 0, texWidth);

        return texAtlas;
    }

    private static class ErrorReportingTask<T> extends CompletableFuture<T> implements Runnable {
        private final Callable<T> task;

        public ErrorReportingTask(Callable<T> task) {
            this.task = task;
        }

        @Override
        public void run() {
            T returnVal = null;
            try {
                if (isCancelled()) {
                    return;
                }
                returnVal = task.call();
            } catch (Exception ex) {
                ShadowMap.getLogger().warn("Exception thrown in Map task", ex);
                completeExceptionally(ex);
            } catch (Throwable ex) {
                ShadowMap.getLogger().warn("Exception thrown in Map task", ex);
                completeExceptionally(ex);
                throw ex;
            } finally {
                complete(returnVal);
            }
        }
    }

    private class LockingTask<T> extends CompletableFuture<T> implements Runnable {
        private final Lock lock;
        private final Callable<T> task;

        public LockingTask(Lock lock, Callable<T> task) {
            this.lock = lock;
            this.task = task;
        }

        @Override
        public void run() {
            T returnVal = null;
            try {
                lock.lockInterruptibly();
                returnVal = task.call();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                ShadowMap.getLogger().error("Map task interrupted");
                completeExceptionally(ex);
            } catch (Exception ex) {
                ShadowMap.getLogger().error("Exception thrown in Map task", ex);
                completeExceptionally(ex);
            } catch (Throwable ex) {
                ShadowMap.getLogger().error("Exception thrown in Map task", ex);
                completeExceptionally(ex);
                throw ex;
            } finally {
                lock.unlock();
                shutdownPhaser.arriveAndDeregister();
                complete(returnVal);
            }
        }
    }

    private static final class PriorityContainer<T> implements Comparable<PriorityContainer<T>> {
        private final MapWorldImpl world;
        private final int regionX, regionZ;
        private final Callable<T> task;
        private long priority;

        private PriorityContainer(MapWorldImpl world, int regionX, int regionZ, Callable<T> task) {
            this.world = world;
            this.regionX = regionX;
            this.regionZ = regionZ;
            this.task = task;
            this.priority = world.getRenderPriority(regionX, regionZ);
        }

        @Override
        public int compareTo(@NotNull MapManagerImpl.PriorityContainer o) {
            return Long.compare(priority, o.priority);
        }
    }

    private class PriorityLockingTask<T> extends CompletableFuture<T> implements Runnable {
        private final PriorityBlockingQueue<PriorityContainer<T>> queue;
        private final boolean exclusiveLock;

        private PriorityLockingTask(PriorityBlockingQueue<PriorityContainer<T>> queue, boolean exclusiveLock) {
            this.queue = queue;
            this.exclusiveLock = exclusiveLock;
        }

        @Override
        public void run() {
            PriorityContainer<T> taskContainer;
            Lock lock = null;
            T returnVal = null;
            try {
                taskContainer = queue.take();
                lock = exclusiveLock
                        ? getRegionLock(taskContainer.regionX, taskContainer.regionZ).writeLock()
                        : getRegionLock(taskContainer.regionX, taskContainer.regionZ).readLock();
                lock.lockInterruptibly();
                returnVal = taskContainer.task.call();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                ShadowMap.getLogger().error("Map task interrupted");
                completeExceptionally(ex);
            } catch (Exception ex) {
                ShadowMap.getLogger().error("Exception thrown in Map task", ex);
                completeExceptionally(ex);
            } catch (Throwable ex) {
                ShadowMap.getLogger().error("Exception thrown in Map task", ex);
                completeExceptionally(ex);
                throw ex;
            } finally {
                if (lock != null) {
                    lock.unlock();
                }
                shutdownPhaser.arriveAndDeregister();
                complete(returnVal);
            }
        }
    }

    private class ShutdownPhaser extends Phaser {
        @Override
        protected boolean onAdvance(int phase, int registeredParties) {
            return shutdown.get();
        }
    }

    private record CleanupSorter(int index, RegionContainerImpl region, int maxLoad, long renderPrio, long lastRead) implements Comparable<CleanupSorter> {
        @Override
        public int compareTo(@NotNull MapManagerImpl.CleanupSorter o) {
            int c = -Integer.compare(maxLoad, o.maxLoad);
            if (c != 0) {
                return c;
            }
            c = Long.compare(renderPrio, o.renderPrio);
            if (c != 0) {
                return c;
            }
            c = Long.compare(lastRead, o.lastRead);
            if (c != 0) {
                return c;
            }
            return index - o.index;
        }
    }
}
