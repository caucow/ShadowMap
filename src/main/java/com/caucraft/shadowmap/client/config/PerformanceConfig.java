package com.caucraft.shadowmap.client.config;

import com.caucraft.shadowmap.client.config.values.ConfigSection;
import com.caucraft.shadowmap.client.config.values.EnumValue;
import com.caucraft.shadowmap.client.config.values.IntValue;

public class PerformanceConfig {
    public final EnumValue<PerformanceMode> performanceMode;
    public final IntValue blockMemoryMB;
    public final IntValue blockTimeoutS;
    public final IntValue metaMemoryMB;
    public final IntValue metaTimeoutS;
    public final IntValue textureMemoryMB;
    public final IntValue textureTimeoutS;

    public PerformanceConfig(ConfigSection section) {
        this.performanceMode = section.getEnum("performanceMode", PerformanceMode.BALANCED, PerformanceMode.class);
        this.blockMemoryMB = section.getInt("blockMemory", 256);
        this.blockTimeoutS = section.getInt("blockTimeout", 300);
        this.metaMemoryMB = section.getInt("metaMemory", 128);
        this.metaTimeoutS = section.getInt("metaTimeout", 900);
        this.textureMemoryMB = section.getInt("textureMemory", 512);
        this.textureTimeoutS = section.getInt("textureTimeout", 300);
    }

    public enum PerformanceMode {
        /** Minimal multithreading, render and modification threads are combined. */
        POTATO,
        /** Minimal multithreading with separate thread pools. */
        LOW_IMPACT,
        /** Multiple threads per pool (expected 4 cores, 8 threads). */
        BALANCED,
        /** More threads per pool (expected 6-8 cores, 12-16 threads). */
        HIGH_IMPACT,
        /** Use as many threads as possible, leave 4-6 for the game (expected too many cores). */
        PCMR,
        /** Custom absolute thread counts with separate thread pools. */
        CUSTOM_ABSOLUTE,
        /** Custom thread counts as a percentage of available with separate thread pools. */
        CUSTOM_PERCENT,
    }
}
