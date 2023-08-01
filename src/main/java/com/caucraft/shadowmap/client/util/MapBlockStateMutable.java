package com.caucraft.shadowmap.client.util;

import com.caucraft.shadowmap.api.util.MapBlockState;

public interface MapBlockStateMutable extends MapBlockState {
    void shadowMap$setTinted(boolean tinted);
    void shadowMap$setColorARGB(int argb);
    boolean shadowMap$isOpacitySet();
    void shadowMap$setOpacity(boolean opaque, int maxOpacity);
}
