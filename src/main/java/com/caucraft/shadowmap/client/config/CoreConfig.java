package com.caucraft.shadowmap.client.config;

import com.caucraft.shadowmap.client.config.values.ConfigSection;
import com.google.gson.JsonObject;

public class CoreConfig {
    private final ConfigSection root;
    public final MinimapConfig minimapConfig;
    public final MapScreenConfig mapScreenConfig;
    public final InfoConfig infoConfig;
    public final GridConfig gridConfig;
    public final PerformanceConfig performanceConfig;
    public final PrivacyConfig privacyConfig;
    public final WaypointConfig waypointConfig;
    public final DebugConfig debugConfig;

    public CoreConfig() {
        ConfigSection root = this.root = new ConfigSection(null);
        this.minimapConfig = new MinimapConfig(root.getSection("minimap"));
        this.mapScreenConfig = new MapScreenConfig(root.getSection("mapScreen"));
        this.infoConfig = new InfoConfig(root.getSection("info"));
        this.gridConfig = new GridConfig(root.getSection("map"));
        this.performanceConfig = new PerformanceConfig(root.getSection("performance"));
        this.privacyConfig = new PrivacyConfig(root.getSection("privacy"));
        this.waypointConfig = new WaypointConfig(root.getSection("waypoint"));
        this.debugConfig = new DebugConfig();
    }

    public boolean isDirty() {
        return root.isDirty();
    }

    public void loadJson(JsonObject root) {
        this.root.loadJson(root, null);
    }

    public JsonObject toJson() {
        return (JsonObject) root.toJson();
    }
}
