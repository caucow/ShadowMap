package com.caucraft.shadowmap.client.config;

import com.caucraft.shadowmap.client.config.values.BooleanValue;
import com.caucraft.shadowmap.client.config.values.ConfigSection;
import com.caucraft.shadowmap.client.config.values.IntValue;

public class GridConfig {
    public final BooleanValue showGridChunks;
    public final IntValue gridColorChunk;
    public final BooleanValue showGridRegions;
    public final IntValue gridColorRegion;
    public final IntValue gridColorRegion32;

    public GridConfig(ConfigSection section) {
        this.showGridChunks = section.getBoolean("showGridChunks", true);
        this.gridColorChunk = section.getInt("gridColorChunk", 0x80808080);
        this.showGridRegions = section.getBoolean("showGridRegions", true);
        this.gridColorRegion = section.getInt("gridColorRegion", 0x80FFFFFF);
        this.gridColorRegion32 = section.getInt("gridColorRegion32", 0x80FFFF00);
    }
}
