package com.caucraft.shadowmap.client.config;

import com.caucraft.shadowmap.client.config.values.BooleanValue;
import com.caucraft.shadowmap.client.config.values.ConfigSection;

public class PrivacyConfig {
    public final BooleanValue enablePrivateMode;
    public final BooleanValue hideCoords;
    public final BooleanValue hideFacing;
    public final BooleanValue hideBiome;
    public final BooleanValue hideBlocks;

    public PrivacyConfig(ConfigSection section) {
        this.enablePrivateMode = section.getBoolean("enablePrivateMode", false);
        this.hideCoords = section.getBoolean("hideCoords", true);
        this.hideFacing = section.getBoolean("hideFacing", true);
        this.hideBiome = section.getBoolean("hideBiome", true);
        this.hideBlocks = section.getBoolean("hideBlocks", true);
    }
}
