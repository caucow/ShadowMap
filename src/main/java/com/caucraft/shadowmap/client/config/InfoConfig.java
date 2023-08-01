package com.caucraft.shadowmap.client.config;

import com.caucraft.shadowmap.client.config.values.BooleanValue;
import com.caucraft.shadowmap.client.config.values.ConfigSection;

public class InfoConfig {
    public BooleanValue enabled;
    public BooleanValue showCoords;
    public BooleanValue showFacing;
    public BooleanValue showLight;
    public BooleanValue showWorld;
    public BooleanValue showDimension;
    public BooleanValue showBiome;
    public BooleanValue showWeather;
    public BooleanValue showDay;
    public BooleanValue showTime;

    public InfoConfig(ConfigSection section) {
        this.enabled = section.getBoolean("enabled", true);
        this.showCoords = section.getBoolean("showCoords", true);
        this.showFacing = section.getBoolean("showFacing", false);
        this.showLight = section.getBoolean("showLight", false);
        this.showWorld = section.getBoolean("showWorld", false);
        this.showDimension = section.getBoolean("showDimension", false);
        this.showBiome = section.getBoolean("showBiome", false);
        this.showDay = section.getBoolean("showDay", true);
        this.showTime = section.getBoolean("showTime", true);
        this.showWeather = section.getBoolean("showWeather", false);
    }

    public enum Alignment {
        LEFT, CENTER, RIGHT;
    }
}
