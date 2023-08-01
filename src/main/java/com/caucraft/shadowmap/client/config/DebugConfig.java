package com.caucraft.shadowmap.client.config;

import com.caucraft.shadowmap.client.config.values.IntValue;
import com.caucraft.shadowmap.client.map.BlocksChunk;

public class DebugConfig {
    public final IntValue surfaceMax;
    public final IntValue caveMax;
    public final IntValue[] surfaceShades;
    public final IntValue[] caveShades;

    public DebugConfig() {
        surfaceMax = new IntValue(null, BlocksChunk.SURFACE_SLOPE_MAX);
        caveMax = new IntValue(null, BlocksChunk.CAVE_SLOPE_MAX);
        surfaceShades = new IntValue[24];
        caveShades = new IntValue[24];
        for (int i = 0; i < 24; i++) {
            surfaceShades[i] = new IntValue(null, BlocksChunk.SURFACE_SLOPE_POINTS[i / 3][i % 3]);
            caveShades[i] = new IntValue(null, BlocksChunk.CAVE_SLOPE_POINTS[i / 3][i % 3]);
        }
    }
}
