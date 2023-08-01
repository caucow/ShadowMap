package com.caucraft.shadowmap.api.map;

public enum RegionFlags {
    /** Flag: All parts of this region must remain loaded because it is part of an ongoing import. */
    IMPORTING(              0x4000_0000),
    /** Flag: All parts of this region must remain loaded because it is in/near render distance */
    RENDER_DISTANCE_FORCED( 0x2000_0000),
    /** Flag: The high-resolution map and all metadata must remain loaded (full map zoomed in) */
    FULLMAP_ZOOM_IN(        0x1000_0000),
    /** Flag: The low-resolution map and some metadata must remain loaded (full map zoomed out) */
    FULLMAP_ZOOM_OUT(       0x0800_0000),
    /** Flag: The low-resolution map must remain loaded (minimap zoomed out) */
    MINIMAP_ZOOM(           0x0400_0000),
    /** Flag: The region (or chunks in it) are scheduled to be saved async. */
    SAVE_SCHEDULED(         0x0200_0000),
    /** Flag: The region (or chunks in it) are scheduled to be updated async. */
    MODIFY_SCHEDULED(       0x0100_0000),
    /** Flag: The region (or chunks in it) are scheduled to be re-rendered */
    RENDER_SCHEDULED(       0x0080_0000),
    /** Flag: The region should not be saved to disk as data still needs to be loaded from disk */
    LOAD_NEEDED(            0x0040_0000),
    /** Flag: The region has been modified in some way that would not normally trigger a save but should be saved. */
    FORCE_SAVE(             0x0000_0002),
    /** Flag: The region should not be saved to disk as it failed to be loaded correctly and might cause corruption */
    IO_FAILED(              0x0000_0001);

    public final int flag;

    RegionFlags(int flag) {
        this.flag = flag;
    }
}
