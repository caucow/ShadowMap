package com.caucraft.shadowmap.client.config;

import com.caucraft.shadowmap.client.config.values.BooleanValue;
import com.caucraft.shadowmap.client.config.values.ConfigSection;
import com.caucraft.shadowmap.client.config.values.EnumValue;
import com.caucraft.shadowmap.client.config.values.IntValue;
import com.caucraft.shadowmap.client.config.values.StringValue;

public class WaypointConfig {
    public final BooleanValue showOnMinimap;
    public final BooleanValue showOnMapScreen;
    public final BooleanValue showInWorld;
    public final EnumValue<Shape> shape;            // TODO
    public final IntValue pointSize;
    public final IntValue maxMapUiScale;
    public final IntValue maxWorldUiScale;
    public final BooleanValue hideCoords;           // TODO + include privacy settings
    public final IntValue defaultVisibleDistance;
    public final IntValue defaultExpandDistance;
    public final BooleanValue deathWaypoints;
    public final StringValue defaultDeathGroupName;
    public final StringValue defaultDeathPointName;
    public final BooleanValue showDistanceInWorld;
    public final BooleanValue showDistanceOnMap;
    public final BooleanValue ignoreVisibleFilterOnMapScreen;
    public final BooleanValue ignoreExpandFilterOnMapScreen;

    public WaypointConfig(ConfigSection section) {
        this.showOnMinimap = section.getBoolean("showOnMinimap", true);
        this.showOnMapScreen = section.getBoolean("showOnMapScreen", true);
        this.showInWorld = section.getBoolean("showInWorld", true);
        this.shape = section.getEnum("shape", Shape.DIAMOND, Shape.class);
        this.pointSize = section.getInt("pointSize", 6);
        this.maxMapUiScale = section.getInt("maxMapUiScale", 2);
        this.maxWorldUiScale = section.getInt("maxWorldUiScale", 2);
        this.hideCoords = section.getBoolean("hideCoords", false);
        this.defaultVisibleDistance = section.getInt("defaultVisibleDistance", 10_000);
        this.defaultExpandDistance = section.getInt("defaultExpandDistance", 1_000);
        this.deathWaypoints = section.getBoolean("deathWaypoints", true);
        this.defaultDeathGroupName = section.getString("defaultDeathGroupName", "Deaths");
        this.defaultDeathPointName = section.getString("defaultDeathPointName", "Death");
        this.showDistanceInWorld = section.getBoolean("showDistanceInWorld", true);
        this.showDistanceOnMap = section.getBoolean("showDistanceOnMap", false);
        this.ignoreVisibleFilterOnMapScreen = section.getBoolean("ignoreVisibleFilterOnMapScreen", false);
        this.ignoreExpandFilterOnMapScreen = section.getBoolean("ignoreExpandFilterOnMapScreen", false);
    }

    public enum Shape {
        DIAMOND,
        SQUARE,
        CIRCLE,
        SHORT_NAME,
    }
}
