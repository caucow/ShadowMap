package com.caucraft.shadowmap.client;

import com.caucraft.shadowmap.api.MapApi;
import com.caucraft.shadowmap.api.MapExtensionInitializer;

public class ShadowMapReentry implements MapExtensionInitializer {
    @Override
    public void onInitializeMap(MapApi mapApi) {
        ShadowMap.getInstance().registerInternalApi(mapApi);
    }
}
