package com.caucraft.shadowmap.client;

import com.caucraft.shadowmap.client.config.CoreConfig;
import com.caucraft.shadowmap.client.gui.MapScreen;
import com.caucraft.shadowmap.client.gui.config.CoreConfigScreen;
import com.caucraft.shadowmap.client.gui.waypoint.EditWaypointScreen;
import com.caucraft.shadowmap.client.gui.waypoint.WaypointListScreen;
import com.caucraft.shadowmap.client.map.MapWorldImpl;
import com.caucraft.shadowmap.client.waypoint.WorldWaypointManager;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

public class Keybinds {
    private final ShadowMap shadowMap;
    public final KeyBinding settings;
    public final KeyBinding map;
    public final KeyBinding recenterMap;
    public final KeyBinding toggleMinimap;
    public final KeyBinding toggleGrid;
    public final KeyBinding zoomIn;
    public final KeyBinding zoomOut;
    public final KeyBinding waypoints;
    public final KeyBinding addWaypoint;
    public final KeyBinding toggleMapWaypoints;
    public final KeyBinding toggleWorldWaypoints;

    Keybinds(ShadowMap shadowMap) {
        this.shadowMap = shadowMap;
        settings = new KeyBinding("Map Settings", GLFW.GLFW_KEY_BACKSLASH, ShadowMap.MOD_ID);
        map = new KeyBinding("Open Map", GLFW.GLFW_KEY_M, ShadowMap.MOD_ID);
        recenterMap = new KeyBinding("Re-center Map", GLFW.GLFW_KEY_SPACE, ShadowMap.MOD_ID);
        toggleMinimap = new KeyBinding("Toggle Minimap", GLFW.GLFW_KEY_UNKNOWN, ShadowMap.MOD_ID);
        toggleGrid = new KeyBinding("Toggle Grid", GLFW.GLFW_KEY_UNKNOWN, ShadowMap.MOD_ID);
        zoomIn = new KeyBinding("Zoom In", GLFW.GLFW_KEY_EQUAL, ShadowMap.MOD_ID);
        zoomOut = new KeyBinding("Zoom Out", GLFW.GLFW_KEY_MINUS, ShadowMap.MOD_ID);
        waypoints = new KeyBinding("Waypoints", GLFW.GLFW_KEY_UNKNOWN, ShadowMap.MOD_ID);
        addWaypoint = new KeyBinding("Add Waypoint", GLFW.GLFW_KEY_UNKNOWN, ShadowMap.MOD_ID);
        toggleMapWaypoints = new KeyBinding("Toggle Map Waypoints", GLFW.GLFW_KEY_UNKNOWN, ShadowMap.MOD_ID);
        toggleWorldWaypoints = new KeyBinding("Toggle World Waypoints", GLFW.GLFW_KEY_UNKNOWN, ShadowMap.MOD_ID);

        KeyBindingHelper.registerKeyBinding(settings);
        KeyBindingHelper.registerKeyBinding(map);
        KeyBindingHelper.registerKeyBinding(recenterMap);
        KeyBindingHelper.registerKeyBinding(toggleMinimap);
        KeyBindingHelper.registerKeyBinding(toggleGrid);
        KeyBindingHelper.registerKeyBinding(zoomIn);
        KeyBindingHelper.registerKeyBinding(zoomOut);
        KeyBindingHelper.registerKeyBinding(waypoints);
        KeyBindingHelper.registerKeyBinding(addWaypoint);
        KeyBindingHelper.registerKeyBinding(toggleMapWaypoints);
        KeyBindingHelper.registerKeyBinding(toggleWorldWaypoints);
    }

    void tickKeybinds(MinecraftClient client) {
        CoreConfig config = shadowMap.getConfig();
        if (settings.wasPressed()) {
            client.send(() -> client.setScreen(new CoreConfigScreen(null)));
        }
        if (map.wasPressed()) {
            client.send(() -> client.setScreen(new MapScreen(shadowMap, null)));
        }
        if (waypoints.wasPressed()) {
            client.send(() -> client.setScreen(new WaypointListScreen(shadowMap, null)));
        }
        if (toggleMinimap.wasPressed()) {
            config.minimapConfig.enabled.toggle();
        }
        if (toggleGrid.wasPressed()) {
            config.minimapConfig.showGrid.toggle();
        }
        if (zoomIn.wasPressed()) {
            float zoom = config.minimapConfig.zoom.get(),
                    min = config.minimapConfig.minZoom.get(),
                    max = config.minimapConfig.maxZoom.get();
            if (zoom < max) {
                zoom = Math.min(max, zoom * 1.25F);
            } else if (config.minimapConfig.zoomWrap.get()) {
                zoom = min;
            }
            config.minimapConfig.zoom.set(zoom);
        }
        if (zoomOut.wasPressed()) {
            float zoom = config.minimapConfig.zoom.get(),
                    min = config.minimapConfig.minZoom.get(),
                    max = config.minimapConfig.maxZoom.get();
            if (zoom > min) {
                zoom = Math.max(min, zoom * 0.8F);
            } else if (config.minimapConfig.zoomWrap.get()) {
                zoom = max;
            }
            config.minimapConfig.zoom.set(zoom);
        }
        if (addWaypoint.wasPressed()) {
            MapWorldImpl world = shadowMap.getMapManager().getCurrentWorld();
            if (world != null) {
                WorldWaypointManager wpManager = world.getWaypointManager();
                Vector3d pos = new Vector3d();
                Entity cameraEntity = client.getCameraEntity();
                if (cameraEntity != null) {
                    Vec3d camPos = cameraEntity.getPos();
                    pos.set(camPos.x, camPos.y, camPos.z);
                }
                client.send(() -> client.setScreen(new EditWaypointScreen(null, wpManager, pos, wpManager.getUniqueID(), false)));
            }
        }
        if (toggleMapWaypoints.wasPressed()) {
            config.waypointConfig.showOnMinimap.toggle();
        }
        if (toggleWorldWaypoints.wasPressed()) {
            config.waypointConfig.showInWorld.toggle();
        }
    }
}
