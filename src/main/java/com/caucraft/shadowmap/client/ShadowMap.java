package com.caucraft.shadowmap.client;

import com.caucraft.shadowmap.api.MapApi;
import com.caucraft.shadowmap.api.MapExtensionInitializer;
import com.caucraft.shadowmap.api.map.MapChunk;
import com.caucraft.shadowmap.api.map.MapRegion;
import com.caucraft.shadowmap.api.storage.StorageAdapter;
import com.caucraft.shadowmap.api.storage.StorageKey;
import com.caucraft.shadowmap.api.ui.FullscreenMapEventHandler;
import com.caucraft.shadowmap.api.ui.MapDecorator;
import com.caucraft.shadowmap.api.util.ServerKey;
import com.caucraft.shadowmap.client.config.CoreConfig;
import com.caucraft.shadowmap.client.gui.IconAtlas;
import com.caucraft.shadowmap.client.gui.MinimapHud;
import com.caucraft.shadowmap.client.importer.ImportManager;
import com.caucraft.shadowmap.client.importer.ImportTask;
import com.caucraft.shadowmap.client.map.MapManagerImpl;
import com.caucraft.shadowmap.client.map.MapWorldImpl;
import com.caucraft.shadowmap.client.map.StorageKeyImpl;
import com.caucraft.shadowmap.client.util.ApiUser;
import com.caucraft.shadowmap.client.util.MapUtils;
import com.caucraft.shadowmap.client.util.TextHelper;
import com.caucraft.shadowmap.client.util.io.ByteBufferInputStream;
import com.caucraft.shadowmap.client.util.io.ByteBufferOutputStream;
import com.caucraft.shadowmap.client.waypoint.Waypoint;
import com.caucraft.shadowmap.client.waypoint.WaypointConstants;
import com.caucraft.shadowmap.client.waypoint.WaypointGroup;
import com.caucraft.shadowmap.client.waypoint.WaypointRenderer;
import com.caucraft.shadowmap.client.waypoint.WorldWaypointManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.Entity;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.VanillaDataPackProvider;
import net.minecraft.server.SaveLoading;
import net.minecraft.server.command.CommandManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Environment(EnvType.CLIENT)
public class ShadowMap implements ClientModInitializer, MapApi {
    public static final String MOD_ID = "shadowmap";
    public static final Identifier RESOURCE_ID = new Identifier(MOD_ID, "core");
    public static final Gson GSON;

    static {
        GSON = new GsonBuilder().setPrettyPrinting().create();
    }

    private boolean hasDefaultRegistries;
    private CompletableFuture<DynamicRegistryManager.Immutable> defaultRegistriesFuture;
    private Registry<Block> defaultBlockRegistry;
    private Registry<Biome> defaultBiomeRegistry;
    private Registry<DimensionType> defaultDimensionRegistry;

    private static ShadowMap instance;
    private static Logger logger;
    private static long lastTickTime;

    private CoreConfig config;
    private ServerKey serverKey;
    private MapManagerImpl mapManager;
    private MinimapHud minimap;
    private Keybinds keybinds;

    private boolean apiInit;
    private MapExtensionInitializer apiModExt;
    private ModMetadata apiModMeta;

    private List<ApiUser<StorageKeyImpl<?, ?, ?>>> apiStorageKeys;
    private List<ApiUser<FullscreenMapEventHandler>> apiFullscreenEventHandlers;
    private List<ApiUser<MapDecorator>> apiMinimapDecorators;
    private List<ApiUser<MapDecorator>> apiFullscreenMapDecorators;
//    private List<ApiUser<MapRenderLayer>> apiMapRenderLayers;

    private WaypointRenderer waypointRenderer;
    private IconAtlas iconAtlas;

    public static Logger getLogger() {
        return logger;
    }

    public static ShadowMap getInstance() {
        return instance;
    }

    public static long getLastTickTimeS() {
        return lastTickTime;
    }

    @Override
    public long getLastTickTime() {
        return lastTickTime;
    }

    public void setServerKey(ServerKey serverKey) {
        this.serverKey = serverKey;
    }

    public ServerKey getServerKey() {
        return serverKey;
    }

    public MapManagerImpl getMapManager() {
        return mapManager;
    }

    public CoreConfig getConfig() {
        return config;
    }

    public Keybinds getKeybinds() {
        return keybinds;
    }

    public IconAtlas getIconAtlas() {
        return iconAtlas;
    }

    @Override
    public void onInitializeClient() {
        System.setProperty("java.awt.headless", "false");
        instance = this;
        logger = LogManager.getLogger(MOD_ID);

        config = new CoreConfig();
        loadConfig();
        mapManager = new MapManagerImpl(this, Paths.get("./scclient/maps").toFile());
        minimap = new MinimapHud(this);

        ClientCommandRegistrationCallback.EVENT.register(this::initCommands);
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return RESOURCE_ID;
            }

            @Override
            public void reload(ResourceManager manager) {
                reloadResources(manager);
            }
        });
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(mapManager);
        ClientLifecycleEvents.CLIENT_STOPPING.register(this::onClientStopping);
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
        HudRenderCallback.EVENT.register(this::onHudRender);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::onPreDebugRender);

        waypointRenderer = new WaypointRenderer(this);
        keybinds = new Keybinds(this);

        ClientLifecycleEvents.CLIENT_STARTED.register(this::onClientStarted);
    }

    private void loadConfig() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("shadowmap.json");
        if (!Files.exists(configPath)) {
            return;
        }
        JsonObject root;
        try (
                FileChannel channel = FileChannel.open(configPath, StandardOpenOption.READ);
                FileLock lock = channel.lock(0, Long.MAX_VALUE, true)
        ) {
            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            buffer = MapUtils.readFileToBuffer(channel, buffer, buffer.limit());
            buffer.flip();
            if (!buffer.hasRemaining()) {
                return;
            }
            root = JsonParser.parseReader(new InputStreamReader(new ByteBufferInputStream(buffer))).getAsJsonObject();
            config.loadJson(root);
        } catch (IOException ex) {
            logger.warn("Could not load configuration", ex);
            return;
        }
        config.loadJson(root);
    }

    private void saveConfig() {
        if (!config.isDirty()) {
            return;
        }
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("shadowmap.json");
        JsonObject root = config.toJson();
        ByteBuffer buffer = null;
        try (
                FileChannel channel = FileChannel.open(configPath, StandardOpenOption.CREATE, StandardOpenOption.READ,
                        StandardOpenOption.WRITE, StandardOpenOption.SYNC);
                FileLock lock = channel.lock()
        ) {
            buffer = mapManager.getIOBufferPool().take();
            ByteBufferOutputStream out = new ByteBufferOutputStream(buffer);
            try (OutputStreamWriter outWriter = new OutputStreamWriter(out)) {
                GSON.toJson(root, outWriter);
            }
            buffer = out.getBuffer();
            buffer.flip();
            MapUtils.writeFileFromBuffer(channel, buffer);
        } catch (IOException ex) {
            logger.warn("Could not load configuration", ex);
            return;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            if (buffer != null) {
                mapManager.getIOBufferPool().release(buffer);
            }
        }
        config.loadJson(root);
    }

    public void scheduleLoadConfig() {
        mapManager.executeGlobalIOTask(() -> {
            loadConfig();
            return null;
        });
    }

    public void scheduleSaveConfig() {
        mapManager.executeGlobalIOTask(() -> {
            saveConfig();
            return null;
        });
    }

    public void onPreResourceRegistration() {
        iconAtlas = new IconAtlas();
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(iconAtlas);
    }

    private void initCommands(
            CommandDispatcher<FabricClientCommandSource> dispatcher,
            CommandRegistryAccess registryAccess) {}

    void registerInternalApi(MapApi mapApi) {
        mapApi.registerFullscreenMapDecorator(waypointRenderer);
        mapApi.registerMinimapDecorator(waypointRenderer);
        mapApi.registerFullscreenMapListener(waypointRenderer);
    }

    private void onClientStarted(MinecraftClient client) {
        // Need to mimic loading dynamic registries during (internal) server
        // creation in order to get biomes. Or something like that. Ree.
        // This cursed monstrosity was derived from
        // CreateWorldScreen.create(MinecraftClient, Screen).
        this.defaultRegistriesFuture = SaveLoading.load(
                new SaveLoading.ServerConfig(
                        new SaveLoading.DataPacks(
                                new ResourcePackManager(new VanillaDataPackProvider()),
                                DataConfiguration.SAFE_MODE,
                                false,
                                true),
                        CommandManager.RegistrationEnvironment.INTEGRATED,
                        2),
                context -> new SaveLoading.LoadContext<>(null, context.dimensionsRegistryManager()),
                (resourceManager, dataPackContents, dynamicRegistries, nullAndVoidBecauseIDontGiveACrapJustGiveMeRegistries) -> {
                    resourceManager.close();
                    return dynamicRegistries.getCombinedRegistryManager();
                },
                Util.getMainWorkerExecutor(),
                client
        ).whenComplete((value, exception) -> {
            if (exception == null) {
                this.defaultBlockRegistry = value.get(RegistryKeys.BLOCK);
                this.defaultBiomeRegistry = value.get(RegistryKeys.BIOME);
                this.defaultDimensionRegistry = value.get(RegistryKeys.DIMENSION_TYPE);
                this.hasDefaultRegistries = true;
            }
        });

        List<EntrypointContainer<MapExtensionInitializer>> extList = FabricLoader.getInstance().getEntrypointContainers("shadowmap", MapExtensionInitializer.class);
        for (EntrypointContainer<MapExtensionInitializer> container : extList) {
            this.apiModMeta = container.getProvider().getMetadata();
            this.apiModExt = container.getEntrypoint();
            apiModExt.onInitializeMap(this);
        }
        this.apiModMeta = null;

        apiInit = true;
        if (apiStorageKeys == null) {
            mapManager.setStorageKeys(new ApiUser[0]);
        } else {
            ListIterator<ApiUser<StorageKeyImpl<?, ?, ?>>> keyIterator = apiStorageKeys.listIterator();
            Map<String, ApiUser<StorageKeyImpl<?, ?, ?>>> matching = new HashMap<>();
            while (keyIterator.hasNext()) {
                ApiUser<StorageKeyImpl<?, ?, ?>> next = keyIterator.next();
                ApiUser<StorageKeyImpl<?, ?, ?>> old = matching.get(next.mod.modId);
                if (old != null) {
                    logger.warn("Storage adapter modId conflict: '" + next.mod.modId + "' between " + old.getClass() + " and " + next.getClass());
                    logger.warn("Only " + old.getClass() + " will be used. This may result in data loss.");
                    keyIterator.remove();
                } else {
                    matching.put(next.mod.modId, next);
                }
            }
            mapManager.setStorageKeys(matching.values().toArray(new ApiUser[matching.size()]));
            logger.info("Registered " + matching.size() + " storage adapters");
            apiStorageKeys = null;
        }
        apiFullscreenEventHandlers = Collections.unmodifiableList(Objects.requireNonNullElseGet(apiFullscreenEventHandlers, Collections::emptyList));
        logger.info("Registered " + apiFullscreenEventHandlers.size() + " fullscreen event handlers");
        apiMinimapDecorators = Collections.unmodifiableList(Objects.requireNonNullElseGet(apiMinimapDecorators, Collections::emptyList));
        logger.info("Registered " + apiMinimapDecorators.size() + " minimap decorators");
        apiFullscreenMapDecorators = Collections.unmodifiableList(Objects.requireNonNullElseGet(apiFullscreenMapDecorators, Collections::emptyList));
        logger.info("Registered " + apiFullscreenMapDecorators.size() + " fullscreen map decorators");
//        apiMapRenderLayers = Collections.unmodifiableList(Objects.requireNonNullElseGet(apiMapRenderLayers, Collections::emptyList));
//        logger.info("Registered " + apiMapRenderLayers.size() + " map renderers");
    }

    private void reloadResources(ResourceManager manager) {

    }

    private void onClientStopping(MinecraftClient client) {
        mapManager.close();
        saveConfig();
    }

    private void onStartClientTick(MinecraftClient mc) {
        long time = lastTickTime = System.currentTimeMillis();
        mapManager.setLastTickTime(time);
    }

    private void onEndClientTick(MinecraftClient client) {
        if (client.player != null && client.world != null) {
            BlockPos cameraPos = client.player.getBlockPos();
            mapManager.setPlayerPosition(cameraPos.getX(), cameraPos.getZ());
            Entity cameraEntity = client.getCameraEntity();
            if (cameraEntity == null) {
                cameraEntity = client.player;
            }
            cameraPos = cameraEntity.getBlockPos();
            mapManager.setCameraPosition(config.minimapConfig, cameraPos.getX(), cameraPos.getZ());
            Vec3d cameraPosVector = cameraEntity.getPos();
            waypointRenderer.cacheWaypoints(mapManager.getCurrentWorld(), cameraPosVector.x, cameraPosVector.y, cameraPosVector.z);

            keybinds.tickKeybinds(client);
        } else {
            waypointRenderer.cacheWaypoints(null, 0, 0, 0);
        }
    }

    private void onHudRender(DrawContext context, float tickDelta) {
        MatrixStack matrixStack = context.getMatrices();
        MinecraftClient client = MinecraftClient.getInstance();
        Matrix4f oldProjectionMatrix = RenderSystem.getProjectionMatrix();
        Matrix4f oneToOneProjection = new Matrix4f().setOrtho(0.0f, client.getFramebuffer().viewportWidth, client.getFramebuffer().viewportHeight, 0.0f, -1000.0f, 1000.0f);
        ClientPlayerEntity player = client.player;

        ImportManager importer = mapManager.getImportManager();
        ImportTask<?> currentTask = importer.getCurrentTask();
        if (currentTask != null && !currentTask.isDone()) {
            String taskState;
            if (currentTask.isDone()) {
                taskState = "Import Finished";
            } else if (currentTask.isReady()) {
                taskState = String.format("Importing %02.02f%%", currentTask.getProgress() * 100);
            } else if (currentTask.isInitializing()) {
                taskState = "Initializing Import...";
            } else {
                taskState = "Import Available";
            }
            TextHelper.get(matrixStack).drawRightAlign(taskState, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
        }

        RenderSystem.setProjectionMatrix(oneToOneProjection, VertexSorter.BY_Z);
        matrixStack = RenderSystem.getModelViewStack();
        matrixStack.push();
        matrixStack.loadIdentity();
        RenderSystem.applyModelViewMatrix();

        if (config.waypointConfig.showInWorld.get()) {
            waypointRenderer.drawHudWaypoints(tickDelta);
        }

        if (config.minimapConfig.enabled.get()) {
            minimap.render(tickDelta);
        }

        matrixStack.pop();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(oldProjectionMatrix, VertexSorter.BY_Z);

        if (config.infoConfig.enabled.get()) {
            minimap.drawInfoHud();
        }
    }

    private void onPreDebugRender(WorldRenderContext context) {
    }

    public void onPlayerDeath(MinecraftClient client, ClientPlayerEntity player) {
        MapWorldImpl currentWorld = mapManager.getCurrentWorld();
        if (currentWorld == null || !config.waypointConfig.deathWaypoints.get()) {
            return;
        }
        Vec3d playerPos = player.getPos();
        WorldWaypointManager waypointManager = currentWorld.getWaypointManager();
        Optional<UUID> deathsGroupId = waypointManager.getDeathsGroupId(config.waypointConfig);
        boolean configureGroup = deathsGroupId.isEmpty() || waypointManager.getWaypointGroup(deathsGroupId.get()).isEmpty();

        if (deathsGroupId.isEmpty()) {
            WaypointGroup deathsGroup = new WaypointGroup(waypointManager, WaypointConstants.DEATHS_GROUP_ID);
            waypointManager.addOrMoveWaypoint(deathsGroup, null);
            deathsGroupId = Optional.of(WaypointConstants.DEATHS_GROUP_ID);
        }

        if (configureGroup || waypointManager.getWaypointGroup(deathsGroupId.get()).get().getChildren().stream().noneMatch((point) -> point.getPos().distanceSquared(playerPos.x, playerPos.y, playerPos.z) < 1)) {
            Waypoint deathPoint = new Waypoint(waypointManager, new Vector3d(playerPos.x, playerPos.y, playerPos.z), 0xBFBFBF);
            deathPoint.setName(config.waypointConfig.defaultDeathPointName.get());
            waypointManager.addOrMoveWaypoint(deathPoint, deathsGroupId.get());
        }

        if (configureGroup) {
            WaypointGroup deathGroup = waypointManager.getWaypointGroup(deathsGroupId.get()).get();
            deathGroup.setName(config.waypointConfig.defaultDeathGroupName.get());
            deathGroup.setAutoResize(false, false);
            deathGroup.setPos(0, 0, 0);
            deathGroup.setDrawExpanded(WaypointGroup.DrawMode.NONE);
            deathGroup.setDrawCollapsed(WaypointGroup.DrawMode.NONE);
        }
    }

    public boolean hasDefaultRegistries() {
        return hasDefaultRegistries;
    }

    public boolean awaitDefaultRegistries() {
        if (hasDefaultRegistries) {
            return true;
        }
        var future = defaultRegistriesFuture;
        if (future == null || future.isCompletedExceptionally()) {
            return false;
        }
        try {
            MinecraftClient.getInstance().runTasks(future::isDone);
            future.join();
        } catch (Exception ex) {
            return false;
        }
        return true;
    }

    public Registry<Block> getDefaultBlockRegistry() {
        return defaultBlockRegistry;
    }

    public Registry<Biome> getDefaultBiomeRegistry() {
        return defaultBiomeRegistry;
    }

    public Registry<DimensionType> getDefaultDimensionRegistry() {
        return defaultDimensionRegistry;
    }

    @Override
    public <RegionType extends MapRegion<ChunkType, ChunkNbtContext>,
            ChunkType extends MapChunk<ChunkNbtContext>,
            ChunkNbtContext>
    StorageKey<RegionType> registerStorageAdapter(StorageAdapter<RegionType, ChunkType, ChunkNbtContext> storageAdapter) {
        if (apiInit) {
            throw new IllegalStateException("Map API resources have already been finalized, resources should only be registered during mod initialization.");
        }
        if (apiModMeta == null) {
            throw new IllegalStateException("Map API registration is not open yet. Registration should be done in MapExtentionInitializer#onMapInitialize.");
        }
        if (apiStorageKeys == null) {
            apiStorageKeys = new ArrayList<>();
        }
        StorageKeyImpl<RegionType, ChunkType, ChunkNbtContext> key = new StorageKeyImpl<>(storageAdapter, apiStorageKeys.size());
        apiStorageKeys.add(new ApiUser<>(apiModExt, apiModMeta, key));
        return key;
    }

    @Override
    public void registerFullscreenMapListener(FullscreenMapEventHandler eventListener) {
        if (apiInit) {
            throw new IllegalStateException("Map API resources have already been finalized, resources should only be registered during mod initialization.");
        }
        if (apiModMeta == null) {
            throw new IllegalStateException("Map API registration is not open yet. Registration should be done in MapExtentionInitializer#onMapInitialize.");
        }
        if (apiFullscreenEventHandlers == null) {
            apiFullscreenEventHandlers = new ArrayList<>();
        }
        apiFullscreenEventHandlers.add(new ApiUser<>(apiModExt, apiModMeta, eventListener));
    }

    @Override
    public void registerMinimapDecorator(MapDecorator mapDecorator) {
        if (apiInit) {
            throw new IllegalStateException("Map API resources have already been finalized, resources should only be registered during mod initialization.");
        }
        if (apiModMeta == null) {
            throw new IllegalStateException("Map API registration is not open yet. Registration should be done in MapExtentionInitializer#onMapInitialize.");
        }
        if (apiMinimapDecorators == null) {
            apiMinimapDecorators = new ArrayList<>();
        }
        apiMinimapDecorators.add(new ApiUser<>(apiModExt, apiModMeta, mapDecorator));
    }

    @Override
    public void registerFullscreenMapDecorator(MapDecorator mapDecorator) {
        if (apiInit) {
            throw new IllegalStateException("Map API resources have already been finalized, resources should only be registered during mod initialization.");
        }
        if (apiModMeta == null) {
            throw new IllegalStateException("Map API registration is not open yet. Registration should be done in MapExtentionInitializer#onMapInitialize.");
        }
        if (apiFullscreenMapDecorators == null) {
            apiFullscreenMapDecorators = new ArrayList<>();
        }
        apiFullscreenMapDecorators.add(new ApiUser<>(apiModExt, apiModMeta, mapDecorator));
    }

//    @Override
//    public void registerRenderLayer(MapRenderLayer renderLayer) {
//        if (apiInit) {
//            throw new IllegalStateException("Map API resources have already been finalized, resources should only be registered during mod initialization.");
//        }
//        if (apiModMeta == null) {
//            throw new IllegalStateException("Map API registration is not open yet. Registration should be done in MapExtentionInitializer#onMapInitialize.");
//        }
//        if (apiMapRenderLayers == null) {
//            apiMapRenderLayers = new ArrayList<>();
//        }
//        apiMapRenderLayers.add(new ApiUser<>(apiModExt, apiModMeta, renderLayer));
//    }

    public List<ApiUser<FullscreenMapEventHandler>> getApiFullscreenEventHandlers() {
        return apiFullscreenEventHandlers;
    }

    public List<ApiUser<MapDecorator>> getApiMinimapDecorators() {
        return apiMinimapDecorators;
    }

    public List<ApiUser<MapDecorator>> getApiFullscreenMapDecorators() {
        return apiFullscreenMapDecorators;
    }

//    public List<ApiUser<MapRenderLayer>> getApiMapRenderLayers() {
//        return apiMapRenderLayers;
//    }
}
