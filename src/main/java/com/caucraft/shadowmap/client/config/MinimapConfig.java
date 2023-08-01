package com.caucraft.shadowmap.client.config;

import com.caucraft.shadowmap.client.config.values.BooleanValue;
import com.caucraft.shadowmap.client.config.values.ConfigSection;
import com.caucraft.shadowmap.client.config.values.EnumValue;
import com.caucraft.shadowmap.client.config.values.FloatValue;
import com.caucraft.shadowmap.client.config.values.IntValue;

public class MinimapConfig {
    public final BooleanValue enabled;
    public final EnumValue<HorizontalAlignment> horizontalAlignment;
    public final EnumValue<VerticalAlignment> verticalAlignment;
    public final IntValue offsetX; // Positive means left-aligned, negative means right aligned.
    public final IntValue offsetY; // Positive means left-aligned, negative means right aligned.
    public final EnumValue<SizeMode> sizeMode;
    public final FloatValue radiusPercent; // [0, 1]
    public final IntValue radiusAbsolute;
    public final IntValue uiScaleMax;
    public final EnumValue<Shape> shape;
    public final BooleanValue lockNorth;
    public final FloatValue zoom;
    public final BooleanValue zoomWrap;
    public final FloatValue minZoom;
    public final FloatValue maxZoom;
    public final BooleanValue showCompass;
    public final IntValue compassUiScaleMax;
    public final BooleanValue showGrid;

    public MinimapConfig(ConfigSection section) {
        this.enabled = section.getBoolean("enabled", false);
        this.horizontalAlignment = section.getEnum("horizontalAlignment", HorizontalAlignment.LEFT, HorizontalAlignment.class);
        this.verticalAlignment = section.getEnum("verticalAlignment", VerticalAlignment.TOP, VerticalAlignment.class);
        this.offsetX = section.getInt("offsetX", 16);
        this.offsetY = section.getInt("offsetY", 16);
        this.sizeMode = section.getEnum("sizeMode", SizeMode.PERCENT, SizeMode.class);
        this.radiusPercent = section.getFloat("radiusPct", 0.08F);
        this.radiusAbsolute = section.getInt("radiusAbs", 110);
        this.uiScaleMax = section.getInt("uiScaleMax", 2);
        this.shape = section.getEnum("shape", Shape.CIRCLE, Shape.class);
        this.lockNorth = section.getBoolean("lockNorth", false);
        this.zoom = section.getFloat("zoom", 1.0F);
        this.zoomWrap = section.getBoolean("zoomWrap", true);
        this.minZoom = section.getFloat("minZoom", 0.125F);
        this.maxZoom = section.getFloat("maxZoom", 16.0F);
        this.showCompass = section.getBoolean("showCompass", true);
        this.compassUiScaleMax = section.getInt("compassUiScaleMax", 1);
        this.showGrid = section.getBoolean("showGrid", false);
    }

    public enum SizeMode {
        /**
         * Size to a percent of the screen's diagonal, or fit to offset bounds
         * if too large.
         */
        PERCENT,
        /**
         * Size to an absolute pixel size, scaled with the UI.
         */
        ABSOLUTE,
    }

    public enum HorizontalAlignment {
        LEFT, CENTER, RIGHT;
    }

    public enum VerticalAlignment {
        TOP, CENTER, BOTTOM;
    }

    public enum Shape {
        SQUARE,
        CIRCLE,
    }

    public enum ZoomEdge {
        STOP,
        WRAP,
    }
}
