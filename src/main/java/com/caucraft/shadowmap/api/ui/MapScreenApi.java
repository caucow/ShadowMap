package com.caucraft.shadowmap.api.ui;

import com.caucraft.shadowmap.api.map.MapWorld;
import com.caucraft.shadowmap.api.util.RenderArea;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.joml.Vector2d;

public abstract class MapScreenApi extends Screen {
    protected MapScreenApi(Text title) {
        super(title);
    }

    public abstract MapWorld getWorld();
    public abstract void uiToWorld(Vector2d position);
    public abstract void worldToUi(Vector2d position);
    public abstract RenderArea getRenderedMapRegions();
    public abstract float getZoom();
    public abstract void setZoom(float zoom);
    public abstract double getCenterX();
    public abstract double getCenterZ();
    public abstract void setCenter(double centerX, double centerZ);
    public abstract void setView(double x1, double z1, double x2, double z2);
}
