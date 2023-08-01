package com.caucraft.shadowmap.client.config;

import com.caucraft.shadowmap.client.config.values.BooleanValue;
import com.caucraft.shadowmap.client.config.values.ConfigSection;
import com.caucraft.shadowmap.client.config.values.FloatValue;

public class MapScreenConfig {
    public final BooleanValue showGrid;
    public final BooleanValue showInfo;
    public final BooleanValue showInfoCoords;
    public final BooleanValue showInfoBiome;
    public final BooleanValue showInfoBlocks;
    public final BooleanValue showInfoBlockstates;
    public final FloatValue minZoom;
    public final FloatValue maxZoom;

    public MapScreenConfig(ConfigSection section) {
        this.showGrid = section.getBoolean("showGrid", false);
        this.showInfo = section.getBoolean("showInfo", true);
        this.showInfoCoords = section.getBoolean("showInfoCoords", true);
        this.showInfoBiome = section.getBoolean("showInfoBiome", false);
        this.showInfoBlocks = section.getBoolean("showInfoBlocks", false);
        this.showInfoBlockstates = section.getBoolean("showInfoBlockStates", false);
        this.minZoom = section.getFloat("minZoom", 0.125F);
        this.maxZoom = section.getFloat("maxZoom", 32.0F);
    }
}
