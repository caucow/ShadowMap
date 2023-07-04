package com.caucraft.shadowmap.client.gui;

import com.caucraft.shadowmap.api.map.MapWorld;
import com.caucraft.shadowmap.api.ui.MapDecorator;
import com.caucraft.shadowmap.api.ui.MapRenderContext;
import com.caucraft.shadowmap.api.util.WorldKey;
import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.config.GridConfig;
import com.caucraft.shadowmap.client.config.InfoConfig;
import com.caucraft.shadowmap.client.config.MinimapConfig;
import com.caucraft.shadowmap.client.config.PrivacyConfig;
import com.caucraft.shadowmap.client.map.MapWorldImpl;
import com.caucraft.shadowmap.client.map.RegionContainerImpl;
import com.caucraft.shadowmap.client.util.ApiUser;
import com.caucraft.shadowmap.client.util.MapFramebuffer;
import com.caucraft.shadowmap.client.util.TextHelper;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.joml.Matrix3x2d;
import org.joml.Matrix3x2dc;
import org.joml.Matrix4f;
import org.joml.Vector2d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class MinimapHud {
    private static final int ANGLE_INCREMENT;
    private static final int CIRCLE_POINTS;
    private static final float[] COS_LOOKUP;
    private static final float[] SIN_LOOKUP;

    static {
        ANGLE_INCREMENT = 5;
        CIRCLE_POINTS = 360 / ANGLE_INCREMENT + 1;
        COS_LOOKUP = new float[CIRCLE_POINTS];
        SIN_LOOKUP = new float[CIRCLE_POINTS];
        for (int angle = 0; angle <= 360; angle += ANGLE_INCREMENT) {
            float angleRads = angle * MathHelper.RADIANS_PER_DEGREE;
            COS_LOOKUP[angle / ANGLE_INCREMENT] = MathHelper.cos(angleRads);
            SIN_LOOKUP[angle / ANGLE_INCREMENT] = MathHelper.sin(angleRads);
        }
    }

    private final ShadowMap shadowMap;
    private Matrix3x2d tempMatrix;
    private Framebuffer minimapFramebuffer;

    public MinimapHud(ShadowMap shadowMap) {
        this.shadowMap = shadowMap;
        this.tempMatrix = new Matrix3x2d();
    }

    public void render(float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) {
            cameraEntity = client.player;
        }
        if (cameraEntity == null) {
            return;
        }

        Vec3d cameraPos = cameraEntity.getCameraPosVec(tickDelta);
        float cameraYaw = cameraEntity.getYaw(tickDelta);
        drawMinimap(cameraPos.x, cameraPos.z, cameraYaw);
    }

    private void drawCircle(MapRenderContext context, double radius, float lineWidth, int r, int g, int b, int a) {
        double innerRadius = radius - lineWidth / 2;
        double outerRadius = innerRadius + lineWidth;
        Tessellator tess = context.tessellator;
        BufferBuilder buffer = context.buffer;
        // TODO replace with line drawmode once that actually supports lineWidth
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(lineWidth);
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int angleIndex = CIRCLE_POINTS - 1; angleIndex > 0; angleIndex--) {
            context.uiVertex(innerRadius * COS_LOOKUP[angleIndex], innerRadius * SIN_LOOKUP[angleIndex], 0).color(r, g, b, a).next();
            context.uiVertex(outerRadius * COS_LOOKUP[angleIndex], outerRadius * SIN_LOOKUP[angleIndex], 0).color(r, g, b, a).next();
            context.uiVertex(outerRadius * COS_LOOKUP[angleIndex - 1], outerRadius * SIN_LOOKUP[angleIndex - 1], 0).color(r, g, b, a).next();
            context.uiVertex(innerRadius * COS_LOOKUP[angleIndex - 1], innerRadius * SIN_LOOKUP[angleIndex - 1], 0).color(r, g, b, a).next();
        }
        tess.draw();
    }

    private void drawSquare(MapRenderContext context, double width, float lineWidth, int r, int g, int b, int a) {
        double innerRadius = width - lineWidth / 2;
        double outerRadius = innerRadius + lineWidth;
        Tessellator tess = context.tessellator;
        BufferBuilder buffer = context.buffer;
        // TODO replace with line drawmode once that actually supports lineWidth
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(lineWidth);
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        context.uiVertex(-outerRadius, -outerRadius, 0).color(r, g, b, a).next();
        context.uiVertex(-outerRadius, outerRadius, 0).color(r, g, b, a).next();
        context.uiVertex(-innerRadius, innerRadius, 0).color(r, g, b, a).next();
        context.uiVertex(-innerRadius, -innerRadius, 0).color(r, g, b, a).next();
        context.uiVertex(innerRadius, -innerRadius, 0).color(r, g, b, a).next();
        context.uiVertex(innerRadius, innerRadius, 0).color(r, g, b, a).next();
        context.uiVertex(outerRadius, outerRadius, 0).color(r, g, b, a).next();
        context.uiVertex(outerRadius, -outerRadius, 0).color(r, g, b, a).next();
        context.uiVertex(-outerRadius, -outerRadius, 0).color(r, g, b, a).next();
        context.uiVertex(-innerRadius, -innerRadius, 0).color(r, g, b, a).next();
        context.uiVertex(innerRadius, -innerRadius, 0).color(r, g, b, a).next();
        context.uiVertex(outerRadius, -outerRadius, 0).color(r, g, b, a).next();
        context.uiVertex(-innerRadius, innerRadius, 0).color(r, g, b, a).next();
        context.uiVertex(-outerRadius, outerRadius, 0).color(r, g, b, a).next();
        context.uiVertex(outerRadius, outerRadius, 0).color(r, g, b, a).next();
        context.uiVertex(innerRadius, innerRadius, 0).color(r, g, b, a).next();
        tess.draw();
    }

    private void drawTexturedCircle(MapRenderContext context, int radius) {
        BufferBuilder buffer = context.buffer;
        Matrix3x2d tempMatrix = this.tempMatrix.set(context.uiToScreen);
        tempMatrix.scale(1 / context.uiScale);
        buffer.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_TEXTURE);
        context.vertex(tempMatrix, 0.0F, 0.0F, 0.0F).texture(0.5F, 0.5F).next();
        for (int angleIndex = CIRCLE_POINTS - 1; angleIndex >= 0; angleIndex--) {
            float cos = COS_LOOKUP[angleIndex];
            float sin = SIN_LOOKUP[angleIndex];
            context.vertex(tempMatrix, radius * cos, radius * sin, 0.0F).texture(cos * 0.5F + 0.5F, sin * -0.5F + 0.5F).next();
        }
        context.tessellator.draw();
    }

    private void drawTexturedSquare(MapRenderContext context, int radius) {
        double drad = radius / context.uiScale;
        context.buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        context.uiVertex(-drad, -drad, 0.0F).texture(0, 1).next();
        context.uiVertex(-drad, drad, 0.0F).texture(0, 0).next();
        context.uiVertex(drad, drad, 0.0F).texture(1, 0).next();
        context.uiVertex(drad, -drad, 0.0F).texture(1, 1).next();
        context.tessellator.draw();
    }

    private void drawMinimap(double cameraX, double cameraZ, float cameraYaw) {
        MinimapConfig config = shadowMap.getConfig().minimapConfig;
        float zoom = config.zoom.get();
        int radius = getRadius(config);
        MinecraftClient client = MinecraftClient.getInstance();
        MapWorldImpl mapWorld = shadowMap.getMapManager().getCurrentWorld();
        if (mapWorld == null) {
            return;
        }

        // init map framebuffer
        int diameter = radius * 2 + 1;
        if (minimapFramebuffer == null) {
            minimapFramebuffer = new SimpleFramebuffer(diameter, diameter, false, MinecraftClient.IS_SYSTEM_MAC);
        }
        if (minimapFramebuffer.textureWidth != diameter || minimapFramebuffer.textureHeight != diameter || minimapFramebuffer.getColorAttachment() == -1) {
            minimapFramebuffer.resize(diameter, diameter, MinecraftClient.IS_SYSTEM_MAC);
        }

        Matrix4f originalMatrix = RenderSystem.getProjectionMatrix();
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

        // Build render context for minimap framebuffer render
        MapRenderContext context;

        MapRenderContext.Builder builder= switch (config.shape.get()) {
            case CIRCLE -> new MapRenderContext.Builder(mapWorld, MapRenderContext.MapType.MINIMAP_CIRCULAR);
            case SQUARE -> new MapRenderContext.Builder(mapWorld, MapRenderContext.MapType.MINIMAP_RECTANGULAR);
        };
        if (config.lockNorth.get()) {
            builder.setView(cameraX, cameraZ, zoom, 0);
        } else {
            builder.setView(cameraX, cameraZ, zoom, (-cameraYaw + 180) * MathHelper.RADIANS_PER_DEGREE);
        }
        int windowWidth = client.getWindow().getWidth();
        int windowHeight = client.getWindow().getHeight();
        double scaleFactor = Math.min(config.uiScaleMax.get(), client.getWindow().getScaleFactor());
        builder.setTransforms(
                diameter / scaleFactor * 0.5, diameter / scaleFactor * 0.5,
                scaleFactor,
                diameter, diameter);
        builder.setUiSize(new MapRenderContext.RectangleD(
                -radius / scaleFactor,
                -radius / scaleFactor,
                radius / scaleFactor,
                radius / scaleFactor
        ));
        context = builder.getContext();

        Tessellator tess = context.tessellator;
        BufferBuilder buffer = context.buffer;

        // Set up for minimap framebuffer render
        Matrix4f newMatrix = new Matrix4f().setOrtho(0.0f, diameter, diameter, 0.0f, -1000.0f, 1000.0f);
        RenderSystem.setProjectionMatrix(newMatrix, VertexSorter.BY_Z);
        minimapFramebuffer.beginWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_MAX);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.5F);
        RenderSystem.clear(GlConst.GL_COLOR_BUFFER_BIT, true);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);

        // Decorator pre-render
        for (ApiUser<MapDecorator> evtHandler : shadowMap.getApiMinimapDecorators()) {
            try {
                evtHandler.user.preRenderMap(context);
            } catch (Exception ex) {
                ShadowMap.getLogger().error("Exception in " + evtHandler.mod.meta.getName() + " map decorator", ex);
            }
        }

        // Render region textures to minimap texture
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        MapRenderContext.RectangleI regionBounds = context.regionBounds;
        int regionMinX = regionBounds.x1;
        int regionMinZ = regionBounds.z1;
        int regionMaxX = regionBounds.x2;
        int regionMaxZ = regionBounds.z2;
        for (int rz = regionMinZ; rz <= regionMaxZ; rz++) {
            for (int rx = regionMinX; rx <= regionMaxX; rx++) {
                RegionContainerImpl region = mapWorld.getRegion(rx, rz, false, false);
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
        for (ApiUser<MapDecorator> evtHandler : shadowMap.getApiMinimapDecorators()) {
            try {
                evtHandler.user.postRenderMap(context);
            } catch (Exception ex) {
                ShadowMap.getLogger().error("Exception in " + evtHandler.mod.meta.getName() + " map decorator", ex);
            }
        }

        // Gridlines
        if (config.showGrid.get()) {
            drawGrid(context);
        }

        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
        minimapFramebuffer.endWrite();

        {
            int xPos = switch (config.horizontalAlignment.get()) {
                case LEFT -> radius + 1 + config.offsetX.get();
                case CENTER -> windowWidth / 2;
                case RIGHT -> windowWidth - radius - 1 - config.offsetX.get();
            };
            int yPos = switch (config.verticalAlignment.get()) {
                case TOP -> radius + 1 + config.offsetY.get();
                case CENTER -> windowHeight / 2;
                case BOTTOM -> windowHeight - radius - 1 - config.offsetY.get();
            };
            builder.setTransforms(xPos / scaleFactor, yPos / scaleFactor, scaleFactor, windowWidth, windowHeight);
        }
        context = builder.getContext();
        newMatrix.setOrtho(0.0f, windowWidth, windowHeight, 0.0f, -1000.0f, 1000.0f);
        RenderSystem.setProjectionMatrix(newMatrix, VertexSorter.BY_Z);

        // Draw master texture
        client.getFramebuffer().beginWrite(true);
        minimapFramebuffer.beginRead();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, minimapFramebuffer.getColorAttachment());
        switch (config.shape.get()) {
            case CIRCLE -> drawTexturedCircle(context, radius);
            case SQUARE -> drawTexturedSquare(context, radius);
        }
        minimapFramebuffer.endRead();

        Matrix3x2dc tempMatrix;
        if (config.lockNorth.get()) {
            tempMatrix = this.tempMatrix.set(context.uiToScreen).rotate((cameraYaw + 180) * MathHelper.RADIANS_PER_DEGREE);
        } else {
            tempMatrix = context.uiToScreen;
        }

        // TODO replace with better/configurable center marker
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        buffer.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        context.vertex(tempMatrix, 0.0, -5.0, 0.0)    .color(0, 0, 0, 255).next();
        context.vertex(tempMatrix, -4.0, 4.0, 0.0)  .color(0, 0, 0, 255).next();
        context.vertex(tempMatrix, 4.0, 4.0, 0.0)   .color(0, 0, 0, 255).next();
        context.vertex(tempMatrix, 0.0, -3.0, 0.0)    .color(255, 128, 128, 255).next();
        context.vertex(tempMatrix, -2.0, 3.0, 0.0)  .color(255, 128, 128, 255).next();
        context.vertex(tempMatrix, 2.0, 3.0, 0.0)   .color(255, 128, 128, 255).next();
        tess.draw();

        // Map border
        switch (config.shape.get()) {
            case CIRCLE -> drawCircle(context, radius / context.uiScale, (float) (3 / context.uiScale), 64, 64, 64, 255);
            case SQUARE -> drawSquare(context, radius / context.uiScale, (float) (3 / context.uiScale), 64, 64, 64, 255);
        }
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);

        if (config.showCompass.get()) {
            drawCompass(context, config);
        }

        // Decorator decor render
        for (ApiUser<MapDecorator> evtHandler : shadowMap.getApiMinimapDecorators()) {
            try {
                evtHandler.user.renderDecorations(context);
            } catch (Exception ex) {
                ShadowMap.getLogger().error("Exception in " + evtHandler.mod.meta.getName() + " map decorator", ex);
            }
        }

        RenderSystem.setProjectionMatrix(originalMatrix, VertexSorter.BY_Z);
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

    private void drawCompass(MapRenderContext context, MinimapConfig config) {
        float textScale = config.compassUiScaleMax.get();
        if (textScale < context.uiScale) {
            textScale = (float) (textScale / context.uiScale);
        } else {
            textScale = 1.0F;
        }
        TextHelper textHelper = TextHelper.get(context.applyUiMatrix(new Matrix4f()).scale(textScale)).shadow(true).immediate(false);

        Vector2d textVector = new Vector2d();
        context.worldPointTowardsEdge(context.centerX, context.centerZ - 10, 0, textVector);
        context.worldToUi.transformPosition(textVector);
        textVector.div(textScale);
        textHelper.drawCentered("N", (float) textVector.x, (float) textVector.y - 4);
        textHelper.drawCentered("S", (float) -textVector.x, (float) -textVector.y - 4);
        context.worldPointTowardsEdge(context.centerX - 10, context.centerZ, 0, textVector);
        context.worldToUi.transformPosition(textVector);
        textVector.div(textScale);
        textHelper.drawCentered("W", (float) textVector.x, (float) textVector.y - 4);
        textHelper.drawCentered("E", (float) -textVector.x, (float) -textVector.y - 4);
        textHelper.flushBuffers();
    }

    /**
     * Gets the map's radius (or more accurately, half the map's width) in
     * pixels. When the map is in absolute scale mode, this will be the
     * configured radius times the ui scale.
     * @param config the current minimap configuration.
     * @return the map's radius (or more accurately, half the map's width) in
     * pixels.
     */
    public static int getRadius(MinimapConfig config) {
        Window window = MinecraftClient.getInstance().getWindow();
        int radius = switch (config.sizeMode.get()) {
            case ABSOLUTE -> {
                int scale = Math.min(config.uiScaleMax.get(), (int) window.getScaleFactor());
                yield config.radiusAbsolute.get() * scale;
            }
            case PERCENT -> {
                int width = window.getFramebufferWidth();
                int height = window.getFramebufferHeight();
                yield (int) (config.radiusPercent.get() * Math.sqrt(width * width + height * height));
            }
        };
        int availableSpace = Math.min(window.getFramebufferWidth() - 2 * config.offsetX.get(), window.getFramebufferHeight() - 2 * config.offsetY.get());
        if (radius * 2 + 1 > availableSpace) {
            radius = Math.min(radius, Math.max(1, (availableSpace - 1) / 2));
        }
        return radius;
    }

    public void drawInfoHud() {
        Entity camEntity = MinecraftClient.getInstance().getCameraEntity();
        if (camEntity == null) {
            return;
        }
        MapWorld mapWorld = shadowMap.getMapManager().getCurrentWorld();
        WorldKey worldKey = mapWorld == null ? null : mapWorld.getWorldKey();
        Vec3d camPos = camEntity.getPos();
        BlockPos camBPos = camEntity.getBlockPos();
        World world = camEntity.getWorld();
        InfoConfig infoConfig = shadowMap.getConfig().infoConfig;
        PrivacyConfig privacyConfig = shadowMap.getConfig().privacyConfig;
        List<String> lines = new ArrayList<>();
        boolean privacy = privacyConfig.enablePrivateMode.get();
        if (infoConfig.showCoords.get() && (!privacy || !privacyConfig.hideCoords.get())) {
            lines.add(MathHelper.floor(camPos.x) + "  " + MathHelper.floor(camPos.y) + "  " + MathHelper.floor(camPos.z));
        }
        if (infoConfig.showFacing.get() && (!privacy || !privacyConfig.hideFacing.get())) {
            float yaw = camEntity.getYaw() % 360.0F;
            if (yaw > 180.0F) {
                yaw -= 360.0F;
            } else if (yaw < -180.0F) {
                yaw += 360.0F;
            }
            lines.add(String.format("y %.1f / p %.1f", yaw, camEntity.getPitch()));
        }
        if (infoConfig.showLight.get()) {
            lines.add(String.format("BL: %d, SL: %d", world.getLightLevel(LightType.BLOCK, camBPos), world.getLightLevel(LightType.SKY, camBPos)));
        }
        if (worldKey != null) {
            if (infoConfig.showWorld.get()) {
                lines.add(worldKey.worldName());
            }
            if (infoConfig.showDimension.get()) {
                lines.add(worldKey.dimensionName());
            }
        }
        if (infoConfig.showBiome.get() && (!privacy || !privacyConfig.hideBiome.get())) {
            Optional<RegistryKey<Biome>> biomeKey = world.getBiome(camBPos).getKey();
            if (biomeKey.isPresent()) {
                Identifier biomeId = biomeKey.get().getValue();
                lines.add(biomeId.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? biomeId.getPath() : biomeId.toString());
            }
        }
        if (infoConfig.showWeather.get()) {
            if (!world.getLevelProperties().isRaining()) {
                lines.add("Weather: Clear");
            } else if (!world.getLevelProperties().isThundering()) {
                lines.add("Weather: Raining");
            } else {
                lines.add("Weather: Thundering");
            }
        }
        if (infoConfig.showDay.get() || infoConfig.showTime.get()) {
            long ticks = world.getTimeOfDay() + 6000; // 0 ticks is 06:00
            long d = ticks / 24000;
            ticks %= 24000;
            long h = ticks / 1000;
            ticks %= 1000;
            long m = (long) (ticks / (50.0F / 3.0F));
            if (infoConfig.showDay.get() && infoConfig.showTime.get()) {
                lines.add(String.format("Day %d: %02d:%02d", d, h, m));
            } else if (infoConfig.showDay.get()) {
                lines.add("Day " + d);
            } else {
                lines.add(String.format("%02d:%02d", h, m));
            }
        }

        MinimapConfig minimapConfig = shadowMap.getConfig().minimapConfig;
        boolean radarOffset = minimapConfig.enabled.get();
        TextHelper textHelper = TextHelper.get().immediate(false).shadow(true);
        boolean hEdge = false;
        int xPos = 0;
        float align = 0;
        int radius = getRadius(minimapConfig);
        Window window = MinecraftClient.getInstance().getWindow();
        int uiScale = (int) window.getScaleFactor();
        int dw = window.getFramebufferWidth();
        int dh = window.getFramebufferHeight();
        switch (minimapConfig.horizontalAlignment.get()) {
            case LEFT -> {
                hEdge = true;
                xPos = 4;
                align = 0.0F;
            }
            case CENTER -> {
                if (radarOffset) {
                    xPos = dw / 2 + radius + 5 * uiScale;
                    align = 0.0F;
                } else {
                    xPos = dw / 2;
                    align = 0.5F;
                }
            }
            case RIGHT -> {
                hEdge = true;
                xPos = dw - 4;
                align = 1.0F;
            }
        }
        hEdge &= radarOffset;
        int yPos = switch (minimapConfig.verticalAlignment.get()) {
            case TOP -> hEdge ? radius * 2 + 10 * uiScale + minimapConfig.offsetY.get() : 4;
            case CENTER -> hEdge ? dh / 2 + radius + 10 * uiScale + minimapConfig.offsetY.get() : radarOffset ? dh / 2 - radius + minimapConfig.offsetY.get() : dh / 2 - 5 * lines.size() * uiScale;
            case BOTTOM -> hEdge ? dh - radius * 2 - 10 * uiScale * lines.size() - minimapConfig.offsetY.get() : dh - 4 - 10 * lines.size() * uiScale;
        };
        xPos /= uiScale;
        yPos /= uiScale;
        for (String line : lines) {
            textHelper.drawAligned(line, xPos, yPos, align);
            yPos += 10;
        }
        textHelper.flushBuffers();
    }
}
