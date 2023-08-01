package com.caucraft.shadowmap.api.map;

import com.caucraft.shadowmap.api.util.WorldKey;

public interface MapManager {

    MapWorld getCurrentWorld();
    MapWorld getWorld(WorldKey key);

}
