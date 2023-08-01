package com.caucraft.shadowmap.client.gui;

import com.caucraft.shadowmap.api.ui.FullscreenMapEventHandler;
import com.caucraft.shadowmap.api.ui.MapDecorator;
import com.caucraft.shadowmap.api.ui.MapRenderContext;
import com.caucraft.shadowmap.api.ui.MapScreenApi;
import com.caucraft.shadowmap.api.util.EventResult;
import com.caucraft.shadowmap.api.util.RenderArea;
import com.caucraft.shadowmap.client.Keybinds;
import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.config.GridConfig;
import com.caucraft.shadowmap.client.config.MapScreenConfig;
import com.caucraft.shadowmap.client.config.PrivacyConfig;
import com.caucraft.shadowmap.client.gui.component.GotoWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomIconButtonWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomToggleButtonWidget;
import com.caucraft.shadowmap.client.gui.config.CoreConfigScreen;
import com.caucraft.shadowmap.client.gui.importer.ImportsScreen;
import com.caucraft.shadowmap.client.gui.waypoint.WaypointListScreen;
import com.caucraft.shadowmap.client.map.BlocksChunk;
import com.caucraft.shadowmap.client.map.BlocksRegion;
import com.caucraft.shadowmap.client.map.MapWorldImpl;
import com.caucraft.shadowmap.client.map.RegionContainerImpl;
import com.caucraft.shadowmap.client.util.ApiUser;
import com.caucraft.shadowmap.client.util.MapFramebuffer;
import com.caucraft.shadowmap.client.util.MapUtils;
import com.caucraft.shadowmap.client.util.TextHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import org.joml.Matrix3x2d;
import org.joml.Matrix3x2dc;
import org.joml.Matrix4f;
import org.joml.Vector2d;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class MapScreen extends MapScreenApi {

    private final ShadowMap shadowMap;
    private final MapScreenConfig config;
    private final Screen previousScreen;
    private final MapWorldImpl map;
    private float mapZoom;
    private double centerX, centerZ;
    private double uiScaleX;
    private double uiScaleY;

    private RecustomToggleButtonWidget infoButton;
    private RecustomToggleButtonWidget gridButton;
    private RecustomToggleButtonWidget waypointsButton;
    private RecustomIconButtonWidget gotoButton;
    private RecustomIconButtonWidget waypointListButton;
    private RecustomIconButtonWidget importsButton;
    private RecustomIconButtonWidget settingsButton;

    private final Int2ObjectMap<MouseClickData> mouseConsumers;
    private final Int2ObjectMap<FullscreenMapEventHandler> keyConsumers;

    public MapScreen(ShadowMap shadowMap, Screen previousScreen) {
        super(Text.of("ShadowMap"));
        this.shadowMap = shadowMap;
        this.config = shadowMap.getConfig().mapScreenConfig;
        this.previousScreen = previousScreen;
        this.map = shadowMap.getMapManager().getCurrentWorld();
        this.mapZoom = 1.0F;

        MinecraftClient client = MinecraftClient.getInstance();
        IconAtlas atlas = shadowMap.getIconAtlas();

        Entity camera = client.getCameraEntity();
        if (camera == null) {
            camera = client.player;
        }
        if (camera != null) {
            Vec3d pos = camera.getPos();
            centerX = pos.x;
            centerZ = pos.z;
        }

        mouseConsumers = new Int2ObjectArrayMap<>();
        keyConsumers = new Int2ObjectArrayMap<>();

        infoButton = new RecustomToggleButtonWidget(0, 0, 20, 20, null, this::infoClicked, config.showInfo.get()) {
            @Override public String getDisplayText() { return null; }
        };
        infoButton.setIcons(atlas.getIcon(Icons.INFO_OFF), atlas.getIcon(Icons.INFO_ON));
        gridButton = new RecustomToggleButtonWidget(0, 0, 20, 20, null, this::gridClicked, config.showGrid.get()) {
            @Override public String getDisplayText() { return null; }
        };
        gridButton.setIcons(atlas.getIcon(Icons.GRID_OFF), atlas.getIcon(Icons.GRID_ON));
        waypointsButton = new RecustomToggleButtonWidget(0, 0, 20, 20, null, this::waypointsClicked, shadowMap.getConfig().waypointConfig.showOnMapScreen.get()) {
            @Override public String getDisplayText() { return null; }
        };
        waypointsButton.setIcons(atlas.getIcon(Icons.WAYPOINT_OFF), atlas.getIcon(Icons.WAYPOINT_ON));

        gotoButton = new RecustomIconButtonWidget(0, 0, 20, 20, null, this::gotoClicked);
        gotoButton.setIcon(atlas.getIcon(Icons.SET_FOCUS));
        waypointListButton = new RecustomIconButtonWidget(0, 0, 20, 20, null, this::waypointListClicked);
        waypointListButton.setIcon(atlas.getIcon(Icons.WAYPOINT_LIST));
        importsButton = new RecustomIconButtonWidget(0, 0, 20, 20, null, this::importsClicked);
        importsButton.setIcon(atlas.getIcon(Icons.IMPORT));
        settingsButton = new RecustomIconButtonWidget(0, 0, 20, 20, null, this::settingsClicked);
        settingsButton.setIcon(atlas.getIcon(Icons.GEAR_THICK));
    }

    @Override
    protected void init() {
        addDrawableChild(infoButton);
        addDrawableChild(gridButton);
        addDrawableChild(waypointsButton);
        addDrawableChild(gotoButton);
        addDrawableChild(waypointListButton);
        addDrawableChild(importsButton);
        addDrawableChild(settingsButton);

        double zoom = getZoom();
        int windowWidth = client.getWindow().getFramebufferWidth();
        int windowHeight = client.getWindow().getFramebufferHeight();
        this.uiScaleX = (double) width / windowWidth;
        this.uiScaleY = (double) height / windowHeight;
        callEventVoid((handler) -> handler.mapOpened(this));

        resize(client, width, height);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        this.width = width;
        this.height = height;

        int y = height - 25;
        int wx = width - 25;
        settingsButton.setPos(5, y);
        waypointsButton.setPos(wx, y);
        y -= 20;

        importsButton.setPos(5, y);
        gridButton.setPos(wx, y);
        y -= 20;

        waypointListButton.setPos(5, y);
        infoButton.setPos(wx, y);
        y -= 20;

        gotoButton.setPos(5, y);

        int windowWidth = client.getWindow().getFramebufferWidth();
        int windowHeight = client.getWindow().getFramebufferHeight();
        this.uiScaleX = (double) width / windowWidth;
        this.uiScaleY = (double) height / windowHeight;

        callEventVoid((handler) -> handler.mapResized(this));
        updateRenderView();
    }

    @Override
    public void removed() {
        callEventVoid((handler) -> handler.mapViewChanged(this, getRenderedMapRegions(), RenderArea.EMPTY_AREA));
        shadowMap.getMapManager().clearFullmapFocus();
        callEventVoid((handler) -> handler.mapClosed(this));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Keybinds keybinds = shadowMap.getKeybinds();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keybinds.map.matchesKey(keyCode, scanCode)) {
            this.client.send(() -> this.client.setScreen(previousScreen));
            return true;
        }
        if (keybinds.recenterMap.matchesKey(keyCode, scanCode)) {
            Entity camera = client.getCameraEntity();
            if (camera == null) {
                setCenter(0, 0);
            }
            if (camera != null) {
                Vec3d pos = camera.getPos();
                setCenter(pos.x, pos.z);
            }
            return true;
        }
        if (keybinds.toggleMapWaypoints.matchesKey(keyCode, scanCode)) {
            shadowMap.getConfig().waypointConfig.showOnMapScreen.toggle();
            return true;
        }
        if (keybinds.toggleGrid.matchesKey(keyCode, scanCode)) {
            shadowMap.getConfig().mapScreenConfig.showGrid.toggle();
            return true;
        }
        if (keybinds.zoomIn.matchesKey(keyCode, scanCode)) {
            zoomIn(0, 0);
            return true;
        }
        if (keybinds.zoomOut.matchesKey(keyCode, scanCode)) {
            zoomOut(0, 0);
            return true;
        }

        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        FullscreenMapEventHandler consumer = callEventReturnable((handler) -> handler.keyPressed(this, keyCode, scanCode, modifiers));
        keyConsumers.put(keyCode, consumer);
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        boolean result = super.keyReleased(keyCode, scanCode, modifiers);
        FullscreenMapEventHandler consumer = keyConsumers.remove(keyCode);
        if (consumer != null) {
            consumer.keyReleased(this, keyCode, scanCode, modifiers);
        } else {
            callEventReturnable((handler) -> handler.keyReleased(this, keyCode, scanCode, modifiers));
        }
        return result;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);

        double deltaX = mouseX - client.mouse.getX() * uiScaleX;
        double deltaY = mouseY - client.mouse.getY() * uiScaleY;

        for (var pressedButtonEntry : mouseConsumers.int2ObjectEntrySet()) {
            int button = pressedButtonEntry.getIntKey();
            MouseClickData clickData = pressedButtonEntry.getValue();
            if (Math.max(Math.abs(clickData.startX - mouseX) / uiScaleX, Math.abs(clickData.startY - mouseY) / uiScaleY) > 1) {
                clickData.dragged = true;
            }
            if (clickData.dragged) {
                if (clickData.handler == null) {
                    clickData.handler = callEventReturnable((handler) -> handler.mouseDragged(this, button, mouseX, mouseY, deltaX, deltaY));
                    if (clickData.handler == null) {
                        mouseDraggedCustom(mouseX, mouseY, button, deltaX, deltaY);
                    }
                } else {
                    clickData.handler.mouseDragged(this, button, mouseX, mouseY, deltaX, deltaY);
                }
            }
        }

        callEventVoid((handler) -> handler.mouseMoved(this, mouseX, mouseY, deltaX, deltaY));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        FullscreenMapEventHandler consumer = callEventReturnable((handler) -> handler.mousePressed(this, button, mouseX, mouseY));
        mouseConsumers.put(
                button,
                new MouseClickData(
                        mouseX / ((double) client.getWindow().getScaledWidth() / client.getWindow().getWidth()),
                        mouseY / ((double) client.getWindow().getScaledWidth() / client.getWindow().getWidth()),
                        consumer));
        if (consumer != null) {
            return true;
        }

        // TODO remove debug re-rendering
        double zoom = getZoom();
        int windowWidth = client.getWindow().getFramebufferWidth();
        int windowHeight = client.getWindow().getFramebufferHeight();
        int mouseBlockX = MathHelper.floor(client.mouse.getX() / zoom + centerX - windowWidth * 0.5 / zoom);
        int mouseBlockZ = MathHelper.floor(client.mouse.getY() / zoom + centerZ - windowHeight * 0.5 / zoom);
        RegionContainerImpl hoverRegion = map.getRegion(mouseBlockX >> 9, mouseBlockZ >> 9, false, false);
        if (hoverRegion != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                hoverRegion.scheduleRerenderAll(true);
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (super.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }

        MouseClickData clickData = mouseConsumers.remove(button);
        if (clickData == null) {
            return true;
        }

        if (clickData.handler == null) {
            clickData.handler = callEventReturnable((handler) -> handler.mouseReleased(this, button, mouseX, mouseY));
        } else {
            clickData.handler.mouseReleased(this, button, mouseX, mouseY);
        }

        if (!clickData.dragged) {
            if (clickData.handler == null) {
                callEventReturnable((handler) -> handler.mouseClicked(this, button, mouseX, mouseY));
            } else {
                clickData.handler.mouseClicked(this, button, mouseX, mouseY);
            }
        }

        return true;
    }

    private boolean mouseDraggedCustom(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            double zoom = getZoom();
            double scaledZoom = zoom / client.getWindow().getScaleFactor();
            centerX -= deltaX / scaledZoom;
            centerZ -= deltaY / scaledZoom;
            updateRenderView();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // Ignore because it's bad and broken l m a o.
        // The map screen needs to receive the same events as api event
        // handlers, so separating the vanilla drag handler (which only supports
        // one button at a time) from the custom drag handler (which needs to
        // use mouseMoved to capture inputs) makes sense.
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (super.mouseScrolled(mouseX, mouseY, amount)) {
            return true;
        }

        FullscreenMapEventHandler consumer = callEventReturnable((handler) -> handler.mouseScrolled(this, mouseX, mouseY, amount));
        if (consumer != null) {
            return true;
        }

        Window window = client.getWindow();
        double scale = window.getScaleFactor();
        int mouseScaleX = (int) (mouseX * scale);
        int mouseScaleY = (int) (mouseY * scale);
        int wDiv2 = window.getFramebufferWidth() >> 1;
        int hDiv2 = window.getFramebufferHeight() >> 1;
        if (amount > 0) {
            zoomIn(mouseScaleX - wDiv2, mouseScaleY - hDiv2);
        } else if (amount < 0) {
            zoomOut(mouseScaleX - wDiv2, mouseScaleY - hDiv2);
        }
        updateRenderView();

        return true;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);

        if (map == null) {
            super.render(matrices, mouseX, mouseY, delta);
            return;
        }

        Matrix4f sysProjMatrix = RenderSystem.getProjectionMatrix();
        MatrixStack modelStack = RenderSystem.getModelViewStack();
        try {
            int windowWidth = client.getWindow().getFramebufferWidth();
            int windowHeight = client.getWindow().getFramebufferHeight();
            float uiScale = (float) client.getWindow().getScaleFactor();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, windowWidth, windowHeight, 0.0F, -1000.0F, 1000.0F));
            modelStack.push();
            modelStack.loadIdentity();
            RenderSystem.applyModelViewMatrix();
            matrices.push();
            matrices.scale(uiScale, uiScale, 1.0F);
            renderInternal(matrices, mouseX, mouseY, delta);
        } finally {
            matrices.pop();
            modelStack.pop();
            RenderSystem.setProjectionMatrix(sysProjMatrix);
            RenderSystem.applyModelViewMatrix();
        }

        super.render(matrices, mouseX, mouseY, delta);
    }

    private void renderInternal(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        MapScreenConfig config = shadowMap.getConfig().mapScreenConfig;
        int windowWidth = client.getWindow().getFramebufferWidth();
        int windowHeight = client.getWindow().getFramebufferHeight();
        MapRenderContext context;

        MapRenderContext.Builder builder = new MapRenderContext.Builder(map, MapRenderContext.MapType.FULLSCREEN);
        builder.setView(centerX, centerZ, getZoom(), 0.0);
        double scaleFactor = client.getWindow().getScaleFactor();
        builder.setTransforms(
                windowWidth / scaleFactor * 0.5, windowHeight / scaleFactor * 0.5,
                scaleFactor,
                windowWidth, windowHeight);
        builder.setUiSize(new MapRenderContext.RectangleD(
                (-windowWidth >> 1) / scaleFactor,
                (-windowHeight >> 1) / scaleFactor,
                (windowWidth >> 1) / scaleFactor,
                (windowHeight >> 1) / scaleFactor
        ));
        builder.setMouse(client.mouse.getX(), client.mouse.getY());
        context = builder.getContext();

        double zoom = context.zoom;
        int mouseBlockX = MathHelper.floor(context.mouseXWorld);
        int mouseBlockZ = MathHelper.floor(context.mouseZWorld);

        MapRenderContext.RectangleI regionBounds = context.regionBounds;
        int regionMinX = regionBounds.x1;
        int regionMinZ = regionBounds.z1;
        int regionMaxX = regionBounds.x2;
        int regionMaxZ = regionBounds.z2;
        Function<RegionContainerImpl, MapFramebuffer> framebufferFunction;
        if (zoom <= 0.25) {
            // Zoomed out, only use low-res
            framebufferFunction = (region) -> {
                MapFramebuffer lowRes = region.getLowResTexture();
                if (lowRes != null && lowRes.getColorAttachment() > 0) {
                    return lowRes;
                }
                MapFramebuffer highRes = region.getHighResTexture();
                if (highRes != null && highRes.getColorAttachment() > 0) {
                    return highRes;
                }
                return null;
            };
        } else {
            framebufferFunction = (region) -> {
                MapFramebuffer highRes = region.getHighResTexture();
                if (highRes != null && highRes.getColorAttachment() > 0) {
                    return highRes;
                }
                MapFramebuffer lowRes = region.getLowResTexture();
                if (lowRes != null && lowRes.getColorAttachment() > 0) {
                    return lowRes;
                }
                return null;
            };
        }

        // Decorator pre-render
        for (ApiUser<MapDecorator> evtHandler : shadowMap.getApiFullscreenMapDecorators()) {
            try {
                evtHandler.user.preRenderMap(context);
            } catch (Exception ex) {
                ShadowMap.getLogger().error("Exception in " + evtHandler.mod.meta.getName() + " map decorator", ex);
            }
        }

        Tessellator tess = context.tessellator;
        BufferBuilder buffer = context.buffer;
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_MAX);

        // Draw map regions.
        for (int rz = regionMinZ; rz <= regionMaxZ; rz++) {
            for (int rx = regionMinX; rx <= regionMaxX; rx++) {
                RegionContainerImpl region = map.getRegion(rx, rz, false, false);
                if (region == null) {
                    continue;
                }
                double drawX = rx << 9;
                double drawZ = rz << 9;
                MapFramebuffer framebuffer = framebufferFunction.apply(region);
                if (framebuffer != null && framebuffer.getColorAttachment() > 0) {
                    framebuffer.beginRead();
                    RenderSystem.setShaderTexture(0, framebuffer.getColorAttachment());
                    buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
                    context.worldVertex(drawX, drawZ + 512, 0).texture(0.0F, 1.0F).next();
                    context.worldVertex(drawX + 512, drawZ + 512, 0).texture(1.0F, 1.0F).next();
                    context.worldVertex(drawX + 512, drawZ, 0).texture(1.0F, 0.0F).next();
                    context.worldVertex(drawX, drawZ, 0).texture(0.0F, 0.0F).next();
                    tess.draw();
                    framebuffer.endRead();
                }
            }
        }

        // Decorator post-render
        for (ApiUser<MapDecorator> evtHandler : shadowMap.getApiFullscreenMapDecorators()) {
            try {
                evtHandler.user.postRenderMap(context);
            } catch (Exception ex) {
                ShadowMap.getLogger().error("Exception in " + evtHandler.mod.meta.getName() + " map decorator", ex);
            }
        }

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        // Gridlines
        if (config.showGrid.get()) {
            drawGrid(context);
        }

        // Hovered chunk/region highlight
        {
            int shift = zoom >= 1 ? 4 : 9;
            int size = zoom >= 1 ? 16 : 512;
            float drawX = (float) (mouseBlockX >> shift << shift);
            float drawZ = (float) (mouseBlockZ >> shift << shift);
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            context.worldVertex(drawX, drawZ + size, 0).color(255, 255, 255, 32).next();
            context.worldVertex(drawX + size, drawZ + size, 0).color(255, 255, 255, 32).next();
            context.worldVertex(drawX + size, drawZ, 0).color(255, 255, 255, 32).next();
            context.worldVertex(drawX, drawZ, 0).color(255, 255, 255, 32).next();
            tess.draw();
        }

        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity != null) {
            float cameraYaw = cameraEntity.getYaw();
            Vector2d cameraPos = new Vector2d(cameraEntity.getX(), cameraEntity.getZ());
            context.worldToUi.transformPosition(cameraPos);
            Matrix3x2dc tempMatrix = new Matrix3x2d(context.uiToScreen).translate(cameraPos.x, cameraPos.y).rotate((cameraYaw + 180) * MathHelper.RADIANS_PER_DEGREE);

            // TODO replace with better/configurable center marker
            GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            buffer.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            context.vertex(tempMatrix, 0.0, -5.0, 0.0).color(0, 0, 0, 255).next();
            context.vertex(tempMatrix, -4.0, 4.0, 0.0).color(0, 0, 0, 255).next();
            context.vertex(tempMatrix, 4.0, 4.0, 0.0).color(0, 0, 0, 255).next();
            context.vertex(tempMatrix, 0.0, -3.0, 0.0).color(255, 128, 128, 255).next();
            context.vertex(tempMatrix, -2.0, 3.0, 0.0).color(255, 128, 128, 255).next();
            context.vertex(tempMatrix, 2.0, 3.0, 0.0).color(255, 128, 128, 255).next();
            tess.draw();
            GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        }

        // Decorator decor render
        for (ApiUser<MapDecorator> evtHandler : shadowMap.getApiFullscreenMapDecorators()) {
            try {
                evtHandler.user.renderDecorations(context);
            } catch (Exception ex) {
                ShadowMap.getLogger().error("Exception in " + evtHandler.mod.meta.getName() + " map decorator", ex);
            }
        }

        // Hover text
        List<String> hoverText = new ArrayList<>(8);
        hoverText.add(String.format("Zoom: %.3f", zoom));
        if (config.showInfo.get()) {
            addHoverInfo(config, hoverText, mouseBlockX, mouseBlockZ);
        }
        if (!hoverText.isEmpty()) {
            int maxWidth = 0;
            for (String text : hoverText) {
                maxWidth = Math.max(maxWidth, textRenderer.getWidth(text));
            }
            maxWidth += 6;
            int height = hoverText.size() * 10 + 4;
            Matrix4f uiMatrix = matrices.peek().getPositionMatrix();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            buffer.vertex(uiMatrix, 0, height, 0).color(0, 0, 0, 128).next();
            buffer.vertex(uiMatrix, maxWidth, height, 0).color(0, 0, 0, 128).next();
            buffer.vertex(uiMatrix, maxWidth, 0, 0).color(0, 0, 0, 128).next();
            buffer.vertex(uiMatrix, 0, 0, 0).color(0, 0, 0, 128).next();
            tess.draw();
            TextHelper textHelper = TextHelper.get(textRenderer, uiMatrix).shadow(true).immediate(false);
            for (int i = 0; i < hoverText.size(); i++) {
                textHelper.draw(hoverText.get(i), 3, 3 + i * 10);
            }
            textHelper.flushBuffers();
        }
    }

    private void drawGrid(MapRenderContext context) {
        GridConfig gridConfig = shadowMap.getConfig().gridConfig;
        boolean chunks = gridConfig.showGridChunks.get();
        boolean regions = gridConfig.showGridRegions.get();
        if (!(chunks || regions)) {
            return;
        }
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        context.buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        int regionMinX = context.regionBounds.x1;
        int regionMinZ = context.regionBounds.z1;
        int regionMaxX = context.regionBounds.x2;
        int regionMaxZ = context.regionBounds.z2;
        int shift = context.zoom >= 0.03125F ? 9 : 14;
        int a = regionMinX << shift;
        int b = (regionMaxX + 1) << shift;
        int min = regionMinZ << shift;
        int max = (regionMaxZ + 1) << shift;
        int increment = chunks && context.zoom >= 1.0F ? 16 : context.zoom >= 0.03125F ? 512 : 4096;
        int crgb = gridConfig.gridColorChunk.get();
        int rrgb = gridConfig.gridColorRegion.get();
        int r32rgb = gridConfig.gridColorRegion32.get();
        for (int z = min; z <= max; z += increment) {
            int color = (z & 4095) == 0 ? r32rgb : (z & 511) == 0 ? rrgb : crgb;
            context.worldVertex(a, z, 0).color(color).next();
            context.worldVertex(b, z, 0).color(color).next();
        }
        a = regionMinZ << shift;
        b = (regionMaxZ + 1) << shift;
        min = regionMinX << shift;
        max = (regionMaxX + 1) << shift;
        for (int x = min; x <= max; x += increment) {
            int color = (x & 4095) == 0 ? r32rgb : (x & 511) == 0 ? rrgb : crgb;
            context.worldVertex(x, a, 0).color(color).next();
            context.worldVertex(x, b, 0).color(color).next();
        }
        context.tessellator.draw();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    /**
     * Adds text to a list for a hover tooltip or info box.
     * @param hoverText a list to add generated hover text to
     * @param mouseBlockX the X coordinate of the block the mouse is over
     * @param mouseBlockZ the Z coordinate of the block the mouse is over
     * @return true if hover info was added (i.e. chunk was loaded), false
     * otherwise
     */
    private void addHoverInfo(MapScreenConfig config, List<String> hoverText, int mouseBlockX, int mouseBlockZ) {
        PrivacyConfig privacyConfig = shadowMap.getConfig().privacyConfig;
        boolean privacy = privacyConfig.enablePrivateMode.get();
        RegionContainerImpl hoverRegion = map.getRegion(mouseBlockX >> 9, mouseBlockZ >> 9, false, false);
        if (hoverRegion == null) {
            if (config.showInfoCoords.get() && (!privacy || !privacyConfig.hideCoords.get())) {
                hoverText.add(mouseBlockX + " " + mouseBlockZ);
            }
            return;
        }
        BlocksRegion hoverLayer = hoverRegion.getBlocks();
        if (hoverLayer == null) {
            if (config.showInfoCoords.get() && (!privacy || !privacyConfig.hideCoords.get())) {
                hoverText.add(mouseBlockX + "  " + mouseBlockZ);
            }
            return;
        }
        BlocksChunk hoverChunk = hoverLayer.getChunk(mouseBlockX >> 4, mouseBlockZ >> 4, false);
        if (hoverChunk == null) {
            if (config.showInfoCoords.get() && (!privacy || !privacyConfig.hideCoords.get())) {
                hoverText.add(mouseBlockX + "  " + mouseBlockZ);
            }
            return;
        }
        if (config.showInfoCoords.get() && (!privacy || !privacyConfig.hideCoords.get())) {
            int maxHeight = Math.max(Math.max(
                            hoverChunk.getHeight(BlocksChunk.BlockType.OPAQUE, mouseBlockX, mouseBlockZ),
                            hoverChunk.getHeight(BlocksChunk.BlockType.TRANSPARENT, mouseBlockX, mouseBlockZ)),
                    hoverChunk.getHeight(BlocksChunk.BlockType.LIQUID, mouseBlockX, mouseBlockZ));
            hoverText.add(mouseBlockX + "  " + maxHeight + "  " + mouseBlockZ);
        }
        if (config.showInfoBlocks.get() && (!privacy || !privacyConfig.hideBlocks.get())) {
            BlockState block = hoverChunk.getBlock(BlocksChunk.BlockType.OPAQUE, mouseBlockX, mouseBlockZ);
            if (block != null) {
                if (config.showInfoBlockstates.get()) {
                    hoverText.add("S: " + MapUtils.blockStateToString(block));
                } else {
                    hoverText.add("S: " + MapUtils.blockToString(block));
                }
            } else {
                hoverText.add("S: ---");
            }
            block = hoverChunk.getBlock(BlocksChunk.BlockType.TRANSPARENT, mouseBlockX, mouseBlockZ);
            if (block != null) {
                if (config.showInfoBlockstates.get()) {
                    hoverText.add("T: " + MapUtils.blockStateToString(block));
                } else {
                    hoverText.add("T: " + MapUtils.blockToString(block));
                }
            } else {
                hoverText.add("T: ---");
            }
            block = hoverChunk.getBlock(BlocksChunk.BlockType.LIQUID, mouseBlockX, mouseBlockZ);
            if (block != null) {
                if (config.showInfoBlockstates.get()) {
                    hoverText.add("L: " + MapUtils.blockStateToString(block));
                } else {
                    hoverText.add("L: " + MapUtils.blockToString(block));
                }
            } else {
                hoverText.add("L: ---");
            }
        }
        if (config.showInfoBiome.get() && (!privacy || !privacyConfig.hideBiome.get())) {
            Biome biome = hoverChunk.getBiome(mouseBlockX, mouseBlockZ);
            if (biome != null) {
                hoverText.add("B: " + map.getBiomeRegistry().getId(biome));
            } else {
                hoverText.add("B: ---");
            }
        }
    }

    private void updateRenderView() {
        int windowWidth = client.getWindow().getFramebufferWidth();
        int windowHeight = client.getWindow().getFramebufferHeight();
        RenderArea oldArea = getRenderedMapRegions();
        map.setFullmapFocus((int) centerX, (int) centerZ, windowWidth, windowHeight, getZoom());
        RenderArea newArea = getRenderedMapRegions();
        if (!oldArea.equals(newArea)) {
            callEventVoid((handler) -> handler.mapViewChanged(this, oldArea, newArea));
        }
    }

    @Override
    public MapWorldImpl getWorld() {
        return map;
    }

    private void zoomIn(int pixelsFromCenterX, int pixelsFromCenterZ) {
        final float oldZoom = this.mapZoom;
        float newZoom;
        if (hasShiftDown()) {
            if (oldZoom >= 1) {
                /*
                3.2 -> 3 -> 4
                3.0 -> 3 -> 4
                2.8 -> 3 -> 3
                 */
                int zoomInt = Math.round(oldZoom);
                if (oldZoom - zoomInt > -0.1) {
                    zoomInt++;
                }
                newZoom = zoomInt;
            } else {
                /*
                1 / 3.2 -> 1 / 3 -> 1 / 3
                1 / 3.0 -> 1 / 3 -> 1 / 2
                1 / 2.8 -> 1 / 3 -> 1 / 2
                 */
                float inverseZoom = 1 / oldZoom;
                int zoomInt = Math.round(inverseZoom);
                if (inverseZoom - zoomInt < 0.1) {
                    zoomInt--;
                }
                if (zoomInt == 0) {
                    newZoom = 2.0F;
                } else {
                    newZoom = 1.0F / zoomInt;
                }
            }
        } else {
            newZoom = oldZoom * 1.25F;
        }
        this.mapZoom = newZoom = Math.min(newZoom, shadowMap.getConfig().mapScreenConfig.maxZoom.get());
        adjustCenterAfterZoom(pixelsFromCenterX, pixelsFromCenterZ, oldZoom, newZoom);
    }

    private void zoomOut(int pixelsFromCenterX, int pixelsFromCenterZ) {
        final float oldZoom = this.mapZoom;
        float newZoom;
        if (hasShiftDown()) {
            if (oldZoom >= 1) {
                /*
                3.2 -> 3 -> 3
                3.0 -> 3 -> 2
                2.8 -> 3 -> 2
                 */
                int zoomInt = Math.round(oldZoom);
                if (oldZoom - zoomInt < 0.1) {
                    zoomInt--;
                }
                if (zoomInt == 0) {
                    newZoom = 0.5F;
                } else {
                    newZoom = zoomInt;
                }
            } else {
                /*
                1 / 3.2 -> 1 / 3 -> 1 / 4
                1 / 3.0 -> 1 / 3 -> 1 / 4
                1 / 2.8 -> 1 / 3 -> 1 / 3
                 */
                float inverseZoom = 1 / oldZoom;
                int zoomInt = Math.round(inverseZoom);
                if (inverseZoom - zoomInt > -0.1) {
                    zoomInt++;
                }
                newZoom = 1.0F / zoomInt;
            }
        } else {
            newZoom = oldZoom * 0.80F;
        }
        this.mapZoom = newZoom = Math.max(newZoom, shadowMap.getConfig().mapScreenConfig.minZoom.get());;
        adjustCenterAfterZoom(pixelsFromCenterX, pixelsFromCenterZ, oldZoom, newZoom);
    }

    private void adjustCenterAfterZoom(int pixelsFromCenterX, int pixelsFromCenterZ, float oldZoom, float newZoom) {
        centerX += pixelsFromCenterX / oldZoom - pixelsFromCenterX / newZoom;
        centerZ += pixelsFromCenterZ / oldZoom - pixelsFromCenterZ / newZoom;
        updateRenderView();
    }

    /**
     * Transforms the vector from scaled UI coordinates to world coordinates.
     * @param position vector holding (scaled) UI coordinates.
     */
    @Override
    public void uiToWorld(Vector2d position)  { // TODO test
        position.mul(client.getWindow().getScaleFactor());
        position.sub(client.getWindow().getFramebufferWidth() * 0.5, client.getWindow().getFramebufferHeight() * 0.5);
        float zoom = getZoom();
        position.div(zoom);
        position.add(Math.floor(centerX * zoom) / zoom, Math.floor(centerZ * zoom) / zoom);
    }

    /**
     * Transforms the vector from world coordinates to scaled UI coordinates.
     * @param position vector holding world coordinates.
     */
    @Override
    public void worldToUi(Vector2d position) { // TODO test
        float zoom = getZoom();
        position.sub(Math.floor(centerX * zoom) / zoom, Math.floor(centerZ * zoom) / zoom);
        position.mul(zoom);
        position.add(client.getWindow().getFramebufferWidth() >> 1, client.getWindow().getFramebufferHeight() >> 1);
        position.div(client.getWindow().getScaleFactor());
    }

    /**
     * @return the render area encompassing the map regions visible on the map.
     */
    @Override
    public RenderArea getRenderedMapRegions() {
        RenderArea area = map.getRenderArea(MapWorldImpl.LoadLevel.FULL_MAP_ZOOM_IN);
        if (area == RenderArea.EMPTY_AREA) {
            area = map.getRenderArea(MapWorldImpl.LoadLevel.FULL_MAP_ZOOM_OUT);
        }
        return area;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void addScreenNarrations(NarrationMessageBuilder builder) {
        super.addScreenNarrations(builder);
    }

    private void infoClicked(ButtonWidget btn) {
        config.showInfo.toggle();
    }

    private void gridClicked(ButtonWidget btn) {
        config.showGrid.toggle();
    }

    private void waypointsClicked(ButtonWidget btn) {
        shadowMap.getConfig().waypointConfig.showOnMapScreen.toggle();
    }

    private void gotoClicked(ButtonWidget btn) {
        GotoWidget dialog = new GotoWidget(this, width / 2 - 140, height / 3 - 40, 280, 80, width, height, this::remove);
        addDrawableChild(dialog);
        List<Element> children = (List<Element>) children();
        Element last = children.remove(children.size() - 1);
        children.add(0, last);
    }

    private void waypointListClicked(ButtonWidget btn) {
        client.send(() -> client.setScreen(new WaypointListScreen(shadowMap, this)));
    }

    private void importsClicked(ButtonWidget btn) {
        client.send(() -> client.setScreen(new ImportsScreen(this)));
    }

    private void settingsClicked(ButtonWidget btn) {
        client.send(() -> client.setScreen(new CoreConfigScreen(this)));
    }

    /**
     * @return the current zoom level of the map view in pixels per block.
     */
    @Override
    public float getZoom() {
        return mapZoom;
    }

    /**
     * Sets the current zoom level of the map view in pixels per block.
     * @param zoom the new zoom level of the map.
     * @throws IllegalArgumentException if the map is zoomed in to <(1/512)x or
     * >512x.
     */
    @Override
    public void setZoom(float zoom) {
        setZoom(zoom, true);
    }

    private void setZoom(float zoom, boolean updateView) {
        if (zoom < 0.001953125F) {
            zoom = 0.001953125F;
        }
        if (zoom > 512) {
            zoom = 512;
        }
        mapZoom = zoom;
        if (updateView) {
            updateRenderView();
        }
    }

    /**
     * @return the world x coordinate at the center of the map view
     */
    @Override
    public double getCenterX() {
        return centerX;
    }

    /**
     * @return the world z coordinate at the center of the map view
     */
    @Override
    public double getCenterZ() {
        return centerZ;
    }

    /**
     * Centers the map view on the provided coordinates.
     * @param centerX x coordinate in world space.
     * @param centerZ z coordinate in world space.
     */
    @Override
    public void setCenter(double centerX, double centerZ) {
        setCenter(centerX, centerZ, true);
    }

    private void setCenter(double centerX, double centerZ, boolean updateView) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        if (updateView) {
            updateRenderView();
        }
    }

    /**
     * Centers and zooms the map so the specified rectangle is visible in the
     * middle of the screen.
     * @param x1 first world x coordinate.
     * @param z1 first world z coordinate.
     * @param x2 second world x coordinate.
     * @param z2 second world z coordinate.
     */
    @Override
    public void setView(double x1, double z1, double x2, double z2) {
        if (x2 < x1) {
            double x = x1;
            x1 = x2;
            x2 = x;
        }
        if (z2 < z1) {
            double z = z1;
            z1 = z2;
            z2 = z;
        }
        x2++;
        z2++;
        setCenter((x1 + x2) * 0.5, (z1 + z2) * 0.5, false);
        MinecraftClient mc = MinecraftClient.getInstance();
        int frameWidth = mc.getWindow().getFramebufferWidth();
        int frameHeight = mc.getWindow().getFramebufferHeight();
        float newZoom = Math.min(frameWidth / (float) (x2 - x1), frameHeight / (float) (z2 - z1));
        setZoom(MathHelper.clamp(newZoom, 0.001953125F, 512.0F), false);
        updateRenderView();
    }

    private void callEventVoid(Consumer<FullscreenMapEventHandler> listenerCall) {
        for (ApiUser<FullscreenMapEventHandler> evtHandler : shadowMap.getApiFullscreenEventHandlers()) {
            try {
                listenerCall.accept(evtHandler.user);
            } catch (Exception ex) {
                ShadowMap.getLogger().error("Exception in " + evtHandler.mod.meta.getName() + " event handler", ex);
            }
        }
    }

    private FullscreenMapEventHandler callEventReturnable(Function<FullscreenMapEventHandler, EventResult> listenerCall) {
        for (ApiUser<FullscreenMapEventHandler> evtHandler : shadowMap.getApiFullscreenEventHandlers()) {
            try {
                EventResult result = listenerCall.apply(evtHandler.user);
                if (result == null) {
                    result = EventResult.PASS;
                }
                if (result == EventResult.CONSUME) {
                    return evtHandler.user;
                }
            } catch (Exception ex) {
                ShadowMap.getLogger().error("Exception in " + evtHandler.mod.meta.getName() + " event handler", ex);
            }
        }
        return null;
    }

    private static final class MouseClickData {
        final double startX;
        final double startY;
        FullscreenMapEventHandler handler;
        boolean dragged;

        private MouseClickData(double startX, double startY, FullscreenMapEventHandler handler) {
            this.startX = startX;
            this.startY = startY;
            this.handler = handler;
        }
    }
}
