package com.caucraft.shadowmap.api.example;

import com.caucraft.shadowmap.api.MapApi;
import com.caucraft.shadowmap.api.MapExtensionInitializer;

public class ExampleMapExtension implements MapExtensionInitializer {

    @Override
    public void onInitializeMap(MapApi mapApi) {
        MapDecoratorDemo test = new MapDecoratorDemo();
        mapApi.registerFullscreenMapDecorator(test);
        mapApi.registerMinimapDecorator(test);
    }
}
