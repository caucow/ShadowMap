package com.caucraft.shadowmap.api.util;

/**
 * Interface used by internal mixin to cache map render properties in block
 * states.
 */
public interface MapBlockState {
    /**
     * @return true if the BlockState has biome tint applied to it when drawn
     * on the map (ex. grass, water).
     */
    boolean shadowMap$isTinted();

    /**
     * @return the compact integer representing the color of this BlockState on
     * the map in the format {@code 0xAARRGGBB}.
     */
    int shadowMap$getColorARGB();

    /**
     * @return true if the BlockState should be stored and rendered in the
     * solid/opaque layer on the map.
     */
    boolean shadowMap$isOpaque();

    /**
     * @return the maximum opacity of a block on the map as an integer in the
     * range [0, 255].
     */
    int shadowMap$getMaxOpacity();
}
