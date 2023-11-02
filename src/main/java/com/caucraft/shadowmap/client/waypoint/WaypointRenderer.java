package com.caucraft.shadowmap.client.waypoint;

import com.caucraft.shadowmap.api.ui.FullscreenMapEventHandler;
import com.caucraft.shadowmap.api.ui.MapDecorator;
import com.caucraft.shadowmap.api.ui.MapRenderContext;
import com.caucraft.shadowmap.api.ui.MapScreenApi;
import com.caucraft.shadowmap.api.util.EventResult;
import com.caucraft.shadowmap.api.util.WorldKey;
import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.config.WaypointConfig;
import com.caucraft.shadowmap.client.gui.waypoint.EditWaypointScreen;
import com.caucraft.shadowmap.client.map.BlocksChunk;
import com.caucraft.shadowmap.client.map.BlocksRegion;
import com.caucraft.shadowmap.client.map.MapWorldImpl;
import com.caucraft.shadowmap.client.map.RegionContainerImpl;
import com.caucraft.shadowmap.client.mixin.GameRendererAccess;
import com.caucraft.shadowmap.client.util.TextHelper;
import com.google.common.collect.Streams;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2d;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class WaypointRenderer implements MapDecorator, FullscreenMapEventHandler {
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
    private final Vector2d tempVector;
    private final Vector2d tempVector2;
    private final Matrix3x2d tempMatrix;
    private WorldKey cachedWorldKey;
    private final ArrayList<WaypointGroup> miniGroupsCache;
    private final ArrayList<Waypoint> miniPointsCache;
    private final ArrayList<WaypointGroup> screenGroupsCache;
    private final ArrayList<Waypoint> screenPointsCache;
    private final ArrayList<Waypoint> highlightedCache;

    public WaypointRenderer(ShadowMap shadowMap) {
        this.shadowMap = shadowMap;
        this.tempVector = new Vector2d();
        this.tempVector2 = new Vector2d();
        this.tempMatrix = new Matrix3x2d();
        this.miniGroupsCache = new ArrayList<>();
        this.miniPointsCache = new ArrayList<>();
        this.screenGroupsCache = new ArrayList<>();
        this.screenPointsCache = new ArrayList<>();
        this.highlightedCache = new ArrayList<>();
    }

    public void cacheWaypoints(MapWorldImpl world, double cameraX, double cameraY, double cameraZ) {
        WaypointConfig config = shadowMap.getConfig().waypointConfig;
        miniGroupsCache.clear();
        miniPointsCache.clear();
        screenGroupsCache.clear();
        screenPointsCache.clear();
        highlightedCache.clear();
        if (world == null) {
            cachedWorldKey = null;
            return;
        }
        this.cachedWorldKey = world.getWorldKey();
        collectWaypoints(world.getWaypointManager(), cameraX, cameraY, cameraZ, false, false, miniGroupsCache, miniPointsCache);
        if (config.ignoreVisibleFilterOnMapScreen.get() || config.ignoreExpandFilterOnMapScreen.get()) {
            collectWaypoints(world.getWaypointManager(), cameraX, cameraY, cameraZ,
                    config.ignoreVisibleFilterOnMapScreen.get(), config.ignoreExpandFilterOnMapScreen.get(),
                    screenGroupsCache, screenPointsCache);
        } else {
            screenGroupsCache.addAll(miniGroupsCache);
            screenPointsCache.addAll(miniPointsCache);
        }
        world.getWaypointManager().getHighlightedWaypoints(highlightedCache);
        for (Waypoint waypoint : highlightedCache) {
            setCachedLabel(waypoint, cameraX, cameraY, cameraZ);
        }
    }

    private void setCachedLabel(Waypoint point, double cameraX, double cameraY, double cameraZ) {
        String label = point.getShortOrLongName() + " ";
        double dist = point.getPos().distance(cameraX, cameraY, cameraZ);
        if (dist >= 1_000_000.0) {
            label += String.format("%.1fM", dist * 0.000001);
        } else if (dist >= 1_000) {
            label += String.format("%.1fk", dist * 0.001);
        } else if (dist >= 10) {
            label += String.format("%.0fm", dist);
        } else {
            label += String.format("%.1fm", dist);
        }
        point.cachedLabel = label;
    }

    private void collectWaypoints(WorldWaypointManager waypointManager, double cameraX, double cameraY, double cameraZ, boolean ignoreVF, boolean ignoreEF, ArrayList<WaypointGroup> groupsCache, ArrayList<Waypoint> pointsCache) {
        ArrayDeque<Waypoint> waypoints = new ArrayDeque<>(waypointManager.getRootWaypoints());
        while (!waypoints.isEmpty()) {
            Waypoint waypoint = waypoints.poll();
            if (!waypoint.isVisibleAt(cameraX, cameraZ, ignoreVF)) {
                continue;
            }
            if (waypoint instanceof WaypointGroup group) {
                expandGroup(group, cameraX, cameraY, cameraZ, ignoreVF, ignoreEF, groupsCache, pointsCache);
            } else {
                pointsCache.add(waypoint);
                setCachedLabel(waypoint, cameraX, cameraY, cameraZ);
            }
        }
    }

    private void expandGroup(WaypointGroup group, double cameraX, double cameraY, double cameraZ, boolean ignoreVF, boolean ignoreEF, ArrayList<WaypointGroup> groupsCache, ArrayList<Waypoint> pointsCache) {
        boolean expanded = group.isExpandedAt(cameraX, cameraZ, ignoreEF);
        switch (expanded ? group.getDrawExpanded() : group.getDrawCollapsed()) {
            case NONE -> {}
            case POINT -> {
                pointsCache.add(group);
                setCachedLabel(group, cameraX, cameraY, cameraZ);
            }
            case FILTER -> {
                if (group.getExpandFilter() != null) {
                    groupsCache.add(group);
                } else {
                    pointsCache.add(group);
                    setCachedLabel(group, cameraX, cameraY, cameraZ);
                }
            }
            case POINT_FILTER -> {
                if (group.getExpandFilter() != null) {
                    groupsCache.add(group);
                }
                pointsCache.add(group);
                setCachedLabel(group, cameraX, cameraY, cameraZ);
            }
        }
        if (expanded) {
            for (Waypoint child : group.getChildren()) {
                if (child.isVisibleAt(cameraX, cameraZ, ignoreVF)) {
                    if (child instanceof WaypointGroup childGroup) {
                        expandGroup(childGroup, cameraX, cameraY, cameraZ, ignoreVF, ignoreEF, groupsCache, pointsCache);
                    } else {
                        pointsCache.add(child);
                        setCachedLabel(child, cameraX, cameraY, cameraZ);
                    }
                }
            }
        }
    }

    @Override
    public void postRenderMap(MapRenderContext context) {
        WaypointConfig config = shadowMap.getConfig().waypointConfig;
        if ((context.mapType == MapRenderContext.MapType.FULLSCREEN
                ? !config.showOnMapScreen.get()
                : !config.showOnMinimap.get())) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        ArrayList<WaypointGroup> groups;
        ArrayList<Waypoint> highlighted;
        if (context.world.getWorldKey() == cachedWorldKey) {
            groups = context.mapType == MapRenderContext.MapType.FULLSCREEN ? screenGroupsCache : miniGroupsCache;
            highlighted = highlightedCache;
        } else {
            groups = new ArrayList<>();
            highlighted = new ArrayList<>();
            collectWaypoints(((MapWorldImpl) context.world).getWaypointManager(), context.centerX, 0, context.centerZ,
                    context.mapType == MapRenderContext.MapType.FULLSCREEN && config.ignoreVisibleFilterOnMapScreen.get(),
                    context.mapType == MapRenderContext.MapType.FULLSCREEN && config.ignoreExpandFilterOnMapScreen.get(),
                    groups, new ArrayList<>());
            ((MapWorldImpl) context.world).getWaypointManager().getHighlightedWaypoints(highlighted);
        }
        renderMapGroups(context, groups, highlighted);
    }

    @Override
    public void renderDecorations(MapRenderContext context) {
        WaypointConfig config = shadowMap.getConfig().waypointConfig;
        if (context.mapType == MapRenderContext.MapType.FULLSCREEN) {
            if (!config.showOnMapScreen.get()) {
                return;
            }
        } else if (!config.showOnMinimap.get()) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        ArrayList<Waypoint> points;
        ArrayList<Waypoint> highlighted;
        if (context.world.getWorldKey() == cachedWorldKey) {
            points = context.mapType == MapRenderContext.MapType.FULLSCREEN ? screenPointsCache : miniPointsCache;
            highlighted = highlightedCache;
        } else {
            points = new ArrayList<>();
            highlighted = new ArrayList<>();
            collectWaypoints(((MapWorldImpl) context.world).getWaypointManager(), context.centerX, 0, context.centerZ,
                    context.mapType == MapRenderContext.MapType.FULLSCREEN && config.ignoreVisibleFilterOnMapScreen.get(),
                    context.mapType == MapRenderContext.MapType.FULLSCREEN && config.ignoreExpandFilterOnMapScreen.get(),
                    new ArrayList<>(), points);
            ((MapWorldImpl) context.world).getWaypointManager().getHighlightedWaypoints(highlighted);
        }
        renderMapPoints(config, context, points, highlighted);
    }

    private void renderMapGroups(MapRenderContext context, List<WaypointGroup> groups, List<Waypoint> highlighted) {
        Tessellator tess = context.tessellator;
        BufferBuilder buffer = context.buffer;
        buffer.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        for (WaypointGroup group : groups) {
            renderMapGroupFill(context, group);
        }
        for (Waypoint point : highlighted) {
            if (point instanceof WaypointGroup group) {
                renderMapGroupFill(context, group);
            }
        }
        tess.draw();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (WaypointGroup group : groups) {
            renderMapGroupOutline(context, group, false);
        }
        boolean highlight = ShadowMap.getLastTickTimeS() % 1000 < 500;
        for (Waypoint point : highlighted) {
            if (point instanceof WaypointGroup group) {
                renderMapGroupOutline(context, group, highlight);
            }
        }
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        tess.draw();
    }

    private void renderMapGroupFill(MapRenderContext context, WaypointGroup group) {
        WaypointFilter filter = group.getExpandFilter();
        if (filter == null) {
            return;
        }
        int rgb = group.getColorRGB();
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        double x = group.pos.x;
        double z = group.pos.z;
        double radius = filter.getRadius();
        switch (filter.getShape()) {
            case CIRCLE: {
                for (int i = 1; i < CIRCLE_POINTS; i++) {
                    context.worldVertex(x + radius * COS_LOOKUP[i - 1], z + radius * SIN_LOOKUP[i - 1], 0).color(r, g, b, 32).next();
                    context.worldVertex(x, z, 0).color(r, g, b, 32).next();
                    context.worldVertex(x + radius * COS_LOOKUP[i], z + radius * SIN_LOOKUP[i], 0).color(r, g, b, 32).next();
                }
                break;
            }
            case SQUARE: {
                context.worldVertex(x - radius, z - radius, 0).color(r, g, b, 32);
                context.worldVertex(x - radius, z + radius, 0).color(r, g, b, 32);
                context.worldVertex(x + radius, z + radius, 0).color(r, g, b, 32);
                context.worldVertex(x + radius, z + radius, 0).color(r, g, b, 32);
                context.worldVertex(x + radius, z - radius, 0).color(r, g, b, 32);
                context.worldVertex(x - radius, z - radius, 0).color(r, g, b, 32);
                break;
            }
            case OCTAGON: {
                // TODO
                break;
            }
        }
    }

    private void renderMapGroupOutline(MapRenderContext context, WaypointGroup group, boolean highlight) {
        WaypointFilter filter = group.getExpandFilter();
        if (filter == null) {
            return;
        }
        final int gray = highlight ? 255 : 0;
        double x = group.pos.x;
        double z = group.pos.z;
        double radius = filter.getRadius();
        switch (filter.getShape()) {
            case CIRCLE: {
                for (int i = 1; i < CIRCLE_POINTS; i++) {
                    context.worldVertex(x + radius * COS_LOOKUP[i - 1], z + radius * SIN_LOOKUP[i - 1], 0).color(gray, gray, gray, 255).next();
                    context.worldVertex(x + radius * COS_LOOKUP[i], z + radius * SIN_LOOKUP[i], 0).color(gray, gray, gray, 255).next();
                }
                break;
            }
            case SQUARE: {
                context.worldVertex(x - radius, z - radius, 0).color(gray, gray, gray, 255);
                context.worldVertex(x - radius, z + radius, 0).color(gray, gray, gray, 255);
                context.worldVertex(x - radius, z + radius, 0).color(gray, gray, gray, 255);
                context.worldVertex(x + radius, z + radius, 0).color(gray, gray, gray, 255);
                context.worldVertex(x + radius, z + radius, 0).color(gray, gray, gray, 255);
                context.worldVertex(x + radius, z - radius, 0).color(gray, gray, gray, 255);
                context.worldVertex(x + radius, z - radius, 0).color(gray, gray, gray, 255);
                context.worldVertex(x - radius, z - radius, 0).color(gray, gray, gray, 255);
                break;
            }
            case OCTAGON: {

                break;
            }
        }
    }

    private void renderMapPoints(WaypointConfig config, MapRenderContext context, List<Waypoint> points, List<Waypoint> highlighted) {
        Tessellator tess = context.tessellator;
        BufferBuilder buffer = context.buffer;
        float scale = config.maxMapUiScale.get();
        if (scale < context.uiScale) {
            scale = (float) (scale / context.uiScale);
        } else {
            scale = 1.0F;
        }
        TextHelper textHelper = TextHelper.get(context.applyUiMatrix(new Matrix4f()).scale(scale, scale, 1.0F)).immediate(false).shadow(true).color(0xC0FFFFFF);
        // Each triangle of the quad is blended separately, so
        // enabling smoothing creates mid-line artifacts.
//        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        boolean highlight = ShadowMap.getLastTickTimeS() % 1000 < 500;
        if (context.mapType == MapRenderContext.MapType.FULLSCREEN) {
            Waypoint closest = null;
            double closestDistSq = Double.POSITIVE_INFINITY;
            for (Waypoint waypoint : points) {
                renderMapWaypoint(config, context, textHelper, waypoint, false);
                renderMapText(config, context, textHelper, waypoint);

                double distSq = Vector2d.distanceSquared(waypoint.getPos().x, waypoint.getPos().z, context.mouseXWorld, context.mouseZWorld);
                if (distSq < closestDistSq) {
                    closest = waypoint;
                    closestDistSq = distSq;
                }
            }
            for (Waypoint waypoint : highlighted) {
                renderMapWaypoint(config, context, textHelper, waypoint, highlight);
                renderMapText(config, context, textHelper, waypoint);

                double distSq = Vector2d.distanceSquared(waypoint.getPos().x, waypoint.getPos().z, context.mouseXWorld, context.mouseZWorld);
                if (distSq < closestDistSq) {
                    closest = waypoint;
                    closestDistSq = distSq;
                }
            }
            if (closest != null && Math.sqrt(closestDistSq) * context.zoom <= config.pointSize.get() * Math.min(context.uiScale, config.maxMapUiScale.get())) {
                renderMapWaypoint(config, context, textHelper, closest, true);
                renderMapText(config, context, textHelper, closest);
            }
        } else {
            for (Waypoint waypoint : points) {
                renderMapWaypoint(config, context, textHelper, waypoint, false);
                renderMapText(config, context, textHelper, waypoint);
            }
            for (Waypoint waypoint : highlighted) {
                renderMapWaypoint(config, context, textHelper, waypoint, highlight);
                renderMapText(config, context, textHelper, waypoint);
            }
        }

        tess.draw();
//        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        textHelper.flushBuffers();
    }

    private void renderMapWaypoint(WaypointConfig config, MapRenderContext context, TextHelper textHelper, Waypoint waypoint, boolean highlight) {
        Vector2d vec = tempVector;
        vec.set(waypoint.pos.x, waypoint.pos.z);
        context.worldToUi.transformPosition(vec);
        double scale = config.maxMapUiScale.get();
        if (scale < context.uiScale) {
            scale = scale / context.uiScale;
        } else {
            scale = 1.0;
        }
        double x = vec.x;
        double y = vec.y;
        int rgb = waypoint.getColorRGB();
        final int r = rgb >> 16 & 0xFF;
        final int g = rgb >> 8 & 0xFF;
        final int b = rgb & 0xFF;
        final int a = 255;
        double threshold = context.mapType == MapRenderContext.MapType.FULLSCREEN ? 10 : 6;
        int textWidth = textHelper.renderer().getWidth(config.showDistanceOnMap.get() ? waypoint.cachedLabel : waypoint.getShortOrLongName()) + 2;
        int halfWidth = textWidth >> 1;

        int edge = highlight ? 255 : 0;
        if (context.uiContains(x, y, threshold)) {
            int small = config.pointSize.get();
            int big = small + 1;
            Matrix3x2d matrix = tempMatrix;
            matrix.set(context.uiToScreen);
            matrix.translate(vec.x, vec.y);
            matrix.scale(scale);
            context.vertex(matrix, -big, 0, 0).color(edge, edge, edge, a).next();
            context.vertex(matrix, 0, big, 0).color(edge, edge, edge, a).next();
            context.vertex(matrix, big, 0, 0).color(edge, edge, edge, a).next();
            context.vertex(matrix, 0, -big, 0).color(edge, edge, edge, a).next();
            context.vertex(matrix, -small, 0, 0).color(r, g, b, a).next();
            context.vertex(matrix, 0, small, 0).color(r, g, b, a).next();
            context.vertex(matrix, small, 0, 0).color(r, g, b, a).next();
            context.vertex(matrix, 0, -small, 0).color(r, g, b, a).next();

            int textOffset = -small - 6;
            context.vertex(matrix, -halfWidth, textOffset - 5, 0.0F).color(r, g, b, a).next();
            context.vertex(matrix, -halfWidth, textOffset + 5, 0.0F).color(r, g, b, a).next();
            context.vertex(matrix, +halfWidth, textOffset + 5, 0.0F).color(r, g, b, a).next();
            context.vertex(matrix, +halfWidth, textOffset - 5, 0.0F).color(r, g, b, a).next();
        } else {
            context.uiPointTowardsEdge(
                    x, y,
                    threshold * scale,
                    vec);
            Vector2d vec2 = tempVector2;
            vec2.set(0, -10);
            float angle = (float) vec2.angle(vec);
            int leftRight = config.pointSize.get() - 1;
            int tip = leftRight << 1;
            int wing = leftRight >> 1;

            Matrix3x2d matrix = tempMatrix;
            matrix.set(context.uiToScreen);
            matrix.translate(vec.x, vec.y);
            matrix.scale(scale);
            matrix.rotate(angle);
            context.vertex(matrix, leftRight + 1, wing + 1, 0).color(edge, edge, edge, a).next();
            context.vertex(matrix, 0, -tip - 2, 0).color(edge, edge, edge, a).next();
            context.vertex(matrix, 0, -tip - 2, 0).color(edge, edge, edge, a).next();
            context.vertex(matrix, -leftRight - 1, wing + 1, 0).color(edge, edge, edge, a).next();
            context.vertex(matrix, leftRight, wing, 0).color(r, g, b, a).next();
            context.vertex(matrix, 0, -tip, 0).color(r, g, b, a).next();
            context.vertex(matrix, 0, -tip, 0).color(r, g, b, a).next();
            context.vertex(matrix, -leftRight, wing, 0).color(r, g, b, a).next();

            float xAlign;
            float yAlign;
            if (context.mapType == MapRenderContext.MapType.FULLSCREEN) {
                context.uiPointTowardsEdge(x, y, (threshold + (config.pointSize.get() >> 1)) * scale, vec);
                xAlign = textWidth * -0.5F * MathHelper.sin(angle);
                yAlign = 5 * MathHelper.cos(angle);
            } else {
                context.uiPointTowardsEdge(x, y, (threshold - (config.pointSize.get() << 1)) * scale, vec);
                xAlign = textWidth * 0.5F * MathHelper.sin(angle);
                yAlign = -5 * MathHelper.cos(angle);
            }
            matrix.set(context.uiToScreen);
            matrix.translate(vec.x, vec.y);
            matrix.scale(scale);

            context.vertex(matrix, xAlign - halfWidth, yAlign - 5, 0.0F).color(r, g, b, a).next();
            context.vertex(matrix, xAlign - halfWidth, yAlign + 5, 0.0F).color(r, g, b, a).next();
            context.vertex(matrix, xAlign + halfWidth, yAlign + 5, 0.0F).color(r, g, b, a).next();
            context.vertex(matrix, xAlign + halfWidth, yAlign - 5, 0.0F).color(r, g, b, a).next();
        }
    }

    private void renderMapText(WaypointConfig config, MapRenderContext context, TextHelper textHelper, Waypoint waypoint) {
        Vector2d vec = tempVector;
        vec.set(waypoint.pos.x, waypoint.pos.z);
        context.worldToUi.transformPosition(vec);
        double scale = config.maxMapUiScale.get();
        if (scale < context.uiScale) {
            scale = scale / context.uiScale;
        } else {
            scale = 1.0;
        }

        double x = vec.x;
        double y = vec.y;
        String name = config.showDistanceOnMap.get() ? waypoint.cachedLabel : waypoint.getShortOrLongName();
        double threshold = context.mapType == MapRenderContext.MapType.FULLSCREEN ? 10 : 6;
        int textWidth = textHelper.renderer().getWidth(name) + 2;

        if (context.uiContains(x, y, threshold)) {
            textHelper.drawCentered(name, (float) (vec.x / scale), (float) (vec.y / scale) - config.pointSize.get() - 10);
        } else {
            context.uiPointTowardsEdge(
                    x, y,
                    threshold * scale,
                    vec);
            Vector2d vec2 = tempVector2;
            vec2.set(0, -10);
            float angle = (float) vec2.angle(vec);
            float xAlign;
            float yAlign;
            if (context.mapType == MapRenderContext.MapType.FULLSCREEN) {
                context.uiPointTowardsEdge(x, y, (threshold + (config.pointSize.get() >> 1)) * scale, vec);
                xAlign = textWidth * -0.5F * MathHelper.sin(angle);
                yAlign = 5 * MathHelper.cos(angle);
            } else {
                context.uiPointTowardsEdge(x, y, (threshold - (config.pointSize.get() << 1)) * scale, vec);
                xAlign = textWidth * 0.5F * MathHelper.sin(angle);
                yAlign = -5 * MathHelper.cos(angle);
            }
            textHelper.drawCentered(name, (float) (vec.x / scale) + xAlign, (float) (vec.y / scale) + yAlign - 4);
        }
    }

    public void drawHudWaypoints(float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        WaypointConfig config = shadowMap.getConfig().waypointConfig;
        GameRenderer renderer = client.gameRenderer;
        Camera camera = renderer.getCamera();
        GameRendererAccess rendererAccess = (GameRendererAccess) renderer;
        Vec3d camPos = camera.getPos();
        double fov = rendererAccess.shadowMap$getFov(camera, tickDelta, true);
        int dw = client.getWindow().getFramebufferWidth();
        int dh = client.getWindow().getFramebufferHeight();

        // One matrix, straight from world to (unscaled) screen.
        // Put waypoint coords in, get screen coords out with Z
        // being positive inf. if in front and negative if behind.
        // Cull points where Z <= 0 or x, y out of bounds of screen.
        // Or possibly, have directional indicators for points too
        // far from the middle of the screen. Points behind player
        // can have directional angle calculated the same way, but
        // add 180 degrees (since points behind are mirrored across
        // X and Z).
        Matrix4d modelViewProject = new Matrix4d()
                .setPerspective(Math.toRadians(fov), (double) dw / (double) dh, Double.POSITIVE_INFINITY, Double.MAX_VALUE)
                .translateLocal(1.0, -1.0, 0.0)
                .scaleLocal(dw * 0.5, -dh * 0.5, 1.0);
        simulateHurtFlinch(camera, modelViewProject, tickDelta);
        if (client.options.getBobView().getValue()) {
            simulateViewBob(camera, modelViewProject, tickDelta);
        }
        modelViewProject
                .rotateX(Math.toRadians(camera.getPitch()))
                .rotateY(Math.toRadians(camera.getYaw()) + Math.PI)
                .translate(-camPos.x, -camPos.y, -camPos.z);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.getBuffer();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        int uiScale = Math.min((int) client.getWindow().getScaleFactor(), config.maxWorldUiScale.get());
        Matrix4f textMatrix = new Matrix4f();
        if (uiScale > 1) {
            textMatrix.scale(uiScale);
        }
        TextHelper textHelper = TextHelper.get(textMatrix).immediate(false).shadow(true).color(0xC0FFFFFF);
        Vector3d pointVec = new Vector3d();
        int pointSize = config.pointSize.get() * uiScale;
        Iterator<Waypoint> pointIterator = Streams.concat(miniPointsCache.stream(), highlightedCache.stream()).iterator();
        boolean highlight = shadowMap.getLastTickTime() % 1000 < 500;
        while (pointIterator.hasNext()) {
            Waypoint point = pointIterator.next();
            pointVec.set(point.getPos());
            modelViewProject.transformProject(pointVec);
            pointVec.round();
            if (pointVec.z <= 0 || pointVec.x < 0 || pointVec.y < 0 || pointVec.x > dw || pointVec.y > dh) {
                continue;
            }
            int rgb = point.getColorRGB();
            int r = rgb >> 16 & 0xFF;
            int g = rgb >> 8 & 0xFF;
            int b = rgb & 0xFF;
            double distSq = point.getPos().distanceSquared(camPos.x, camPos.y, camPos.z);
            int pr = r;
            int pg = g;
            int pb = b;
            if (highlight && point.isHighlighted()) {
                pr = 255;
                pg = 255;
                pb = 255;
            }
            if (distSq <= 256) {
                int border = distSq <= 16 || highlight && point.isHighlighted() ? 255 : 0;
                buffer.vertex(pointVec.x, pointVec.y - pointSize - 1, 0).color(border, border, border, 255).next();
                buffer.vertex(pointVec.x - pointSize - 1, pointVec.y, 0).color(border, border, border, 255).next();
                buffer.vertex(pointVec.x, pointVec.y + pointSize + 1, 0).color(border, border, border, 255).next();
                buffer.vertex(pointVec.x + pointSize + 1, pointVec.y, 0).color(border, border, border, 255).next();
                buffer.vertex(pointVec.x, pointVec.y - pointSize, 0).color(pr, pg, pb, 255).next();
                buffer.vertex(pointVec.x - pointSize, pointVec.y, 0).color(pr, pg, pb, 255).next();
                buffer.vertex(pointVec.x, pointVec.y + pointSize, 0).color(pr, pg, pb, 255).next();
                buffer.vertex(pointVec.x + pointSize, pointVec.y, 0).color(pr, pg, pb, 255).next();
            } else {
                int inset = MathHelper.ceil(distSq <= 16384 ? pointSize * 2.0F / 3.0F : pointSize * 1.0F / 3.0F);
                buffer.vertex(pointVec.x, pointVec.y - pointSize, 0).color(pr, pg, pb, 255).next();
                buffer.vertex(pointVec.x - pointSize, pointVec.y, 0).color(pr, pg, pb, 255).next();
                buffer.vertex(pointVec.x - pointSize + inset, pointVec.y, 0).color(pr, pg, pb, 255).next();
                buffer.vertex(pointVec.x, pointVec.y - pointSize + inset, 0).color(pr, pg, pb, 255).next();

                buffer.vertex(pointVec.x - pointSize, pointVec.y, 0).color(pr, pg, pb, 255).next();
                buffer.vertex(pointVec.x, pointVec.y + pointSize, 0).color(pr, pg, pb, 255).next();
                buffer.vertex(pointVec.x, pointVec.y + pointSize - inset, 0).color(pr, pg, pb, 255).next();
                buffer.vertex(pointVec.x - pointSize + inset, pointVec.y, 0).color(pr, pg, pb, 255).next();

                buffer.vertex(pointVec.x, pointVec.y + pointSize, 0).color(pr, pg, pb, 255).next();
                buffer.vertex(pointVec.x + pointSize, pointVec.y, 0).color(pr, pg, pb, 255).next();
                buffer.vertex(pointVec.x + pointSize - inset, pointVec.y, 0).color(pr, pg, pb, 255).next();
                buffer.vertex(pointVec.x, pointVec.y + pointSize - inset, 0).color(pr, pg, pb, 255).next();

                buffer.vertex(pointVec.x + pointSize, pointVec.y, 0).color(pr, pg, pb, 255).next();
                buffer.vertex(pointVec.x, pointVec.y - pointSize, 0).color(pr, pg, pb, 255).next();
                buffer.vertex(pointVec.x, pointVec.y - pointSize + inset, 0).color(pr, pg, pb, 255).next();
                buffer.vertex(pointVec.x + pointSize - inset, pointVec.y, 0).color(pr, pg, pb, 255).next();
            }
            String name = config.showDistanceInWorld.get() ? point.cachedLabel : point.getShortOrLongName();
            int halfWidth = ((textHelper.renderer().getWidth(name) >> 1) + 2) * uiScale;
            buffer.vertex(pointVec.x - halfWidth, pointVec.y - pointSize - 11 * uiScale, 0).color(r, g, b, 255).next();
            buffer.vertex(pointVec.x - halfWidth, pointVec.y - pointSize - uiScale, 0).color(r, g, b, 255).next();
            buffer.vertex(pointVec.x + halfWidth, pointVec.y - pointSize - uiScale, 0).color(r, g, b, 255).next();
            buffer.vertex(pointVec.x + halfWidth, pointVec.y - pointSize - 11 * uiScale, 0).color(r, g, b, 255).next();
            textHelper.drawCentered(name, ((int) pointVec.x) / (float) uiScale, ((int) pointVec.y - pointSize - 10 * uiScale) / (float) uiScale);
        }
        tess.draw();
        textHelper.flushBuffers();
    }

    private void simulateHurtFlinch(Camera camera, Matrix4d matrix, float tickDelta) {
        // From GameRenderer#bobViewWhenHurt
        if (!(camera.getFocusedEntity() instanceof LivingEntity livingEntity)) {
            return;
        }
        float knockback;
        float hurtTicks = (float)livingEntity.hurtTime - tickDelta;
        if (livingEntity.isDead()) {
            knockback = Math.min((float)livingEntity.deathTime + tickDelta, 20.0f);
            matrix.rotate(RotationAxis.POSITIVE_Z.rotationDegrees(40.0f - 8000.0f / (knockback + 200.0f)));
        }
        if (hurtTicks < 0.0f) {
            return;
        }
        hurtTicks /= (float)livingEntity.maxHurtTime;
        hurtTicks = MathHelper.sin(hurtTicks * hurtTicks * hurtTicks * hurtTicks * (float)Math.PI);
        knockback = livingEntity.knockbackVelocity;
        matrix.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(-knockback));
        matrix.rotate(RotationAxis.POSITIVE_Z.rotationDegrees(-hurtTicks * 14.0f));
        matrix.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(knockback));
    }

    private void simulateViewBob(Camera camera, Matrix4d matrix, float tickDelta) {
        // From GameRenderer#bobView
        if (!(camera.getFocusedEntity() instanceof PlayerEntity playerEntity)) {
            return;
        }
        float hSpeedDelta = playerEntity.horizontalSpeed - playerEntity.prevHorizontalSpeed;
        float hSpeedFrame = -(playerEntity.horizontalSpeed + hSpeedDelta * tickDelta);
        float strideLerp = MathHelper.lerp(tickDelta, playerEntity.prevStrideDistance, playerEntity.strideDistance);
        matrix.translate(MathHelper.sin(hSpeedFrame * (float)Math.PI) * strideLerp * 0.5f, -Math.abs(MathHelper.cos(hSpeedFrame * (float)Math.PI) * strideLerp), 0.0f);
        matrix.rotate(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(hSpeedFrame * (float)Math.PI) * strideLerp * 3.0f));
        matrix.rotate(RotationAxis.POSITIVE_X.rotationDegrees(Math.abs(MathHelper.cos(hSpeedFrame * (float)Math.PI - 0.2f) * strideLerp) * 5.0f));
    }

    @Override
    public EventResult mouseClicked(MapScreenApi screen, int button, double mouseX, double mouseY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return EventResult.PASS;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        WaypointConfig config = shadowMap.getConfig().waypointConfig;
        MapWorldImpl world = (MapWorldImpl) screen.getWorld();
        Vector2d pos = new Vector2d(client.mouse.getX(), client.mouse.getY());
        pos.div(client.getWindow().getScaleFactor());
        screen.uiToWorld(pos);

        Waypoint closest = null;
        double closestDistSq = Double.POSITIVE_INFINITY;

        Iterator<Waypoint> pointIterator;
        if (world.getWorldKey() == cachedWorldKey) {
            pointIterator = Streams.concat(highlightedCache.stream(), screenPointsCache.stream()).iterator();
        } else {
            ArrayList<Waypoint> points = new ArrayList<>();
            collectWaypoints(world.getWaypointManager(), screen.getCenterX(), 0, screen.getCenterZ(),
                    config.ignoreVisibleFilterOnMapScreen.get(), config.ignoreExpandFilterOnMapScreen.get(),
                    new ArrayList<>(), points);
            pointIterator = Streams.concat(world.getWaypointManager().getHighlightedWaypoints().stream(), points.stream()).iterator();
        }

        while (pointIterator.hasNext()) {
            Waypoint waypoint = pointIterator.next();
            double distSq = Vector2d.distanceSquared(waypoint.getPos().x, waypoint.getPos().z, pos.x, pos.y);
            if (distSq < closestDistSq) {
                closest = waypoint;
                closestDistSq = distSq;
            }
        }
        double maxDistSq = Math.min(client.getWindow().getScaleFactor(), shadowMap.getConfig().waypointConfig.maxMapUiScale.get());
        maxDistSq *= shadowMap.getConfig().waypointConfig.pointSize.get();
        if (closest != null && Math.sqrt(closestDistSq) * screen.getZoom() <= maxDistSq) {
            final Waypoint closestFinal = closest;
            client.send(() -> client.setScreen(new EditWaypointScreen(screen, world.getWaypointManager(), closestFinal)));
        } else {
            int mouseBlockX = MathHelper.floor(pos.x);
            int mouseBlockZ = MathHelper.floor(pos.y);
            RegionContainerImpl region = world.getRegion(mouseBlockX >> 9, mouseBlockZ >> 9, false, false);
            BlocksRegion regionBlocks = region == null ? null : region.getBlocks();
            BlocksChunk chunkBlocks = regionBlocks == null ? null : regionBlocks.getChunk(mouseBlockX >> 4, mouseBlockZ >> 4, false);
            int wpY = chunkBlocks == null ? -64 : Math.max(
                    Math.max(
                            chunkBlocks.getHeight(BlocksChunk.BlockType.OPAQUE, mouseBlockX, mouseBlockZ),
                            chunkBlocks.getHeight(BlocksChunk.BlockType.TRANSPARENT, mouseBlockX, mouseBlockZ)),
                    chunkBlocks.getHeight(BlocksChunk.BlockType.LIQUID, mouseBlockX, mouseBlockZ)
            );
            client.send(() -> client.setScreen(new EditWaypointScreen(screen, world.getWaypointManager(), new Vector3d(mouseBlockX + 0.5, wpY + 1, mouseBlockZ + 0.5), null, false)));
        }
        return EventResult.CONSUME;
    }
}
