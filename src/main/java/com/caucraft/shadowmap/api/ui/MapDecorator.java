package com.caucraft.shadowmap.api.ui;

/**
 * Referenced whenever a map is drawn on screen to add decorations like
 * region gridlines, waypoints, or compass directions.
 */
public interface MapDecorator {

    /**
     * Called before the map is rendered to draw decorations meant to be covered
     * by loaded regions on the map. This is drawn on the same framebuffer as
     * region tiles.
     * @param context render context before rendering the map
     */
    default void preRenderMap(MapRenderContext context) {}

    /**
     * Called after the map is rendered to draw decorations on top of the map.
     * This is drawn on the same framebuffer as region tiles. Example usage
     * includes chunk or region gridlines.
     * @param context render context after rendering the map
     */
    default void postRenderMap(MapRenderContext context) {}

    /**
     * Called after the map is rendered to add additional UI decorations over
     * the map UI. This is drawn on the UI framebuffer over elements such as the
     * minimap border. Example usage includes compass directions or waypoints.
     * @param context render context after rendering the map
     */
    default void renderDecorations(MapRenderContext context) {}
}
