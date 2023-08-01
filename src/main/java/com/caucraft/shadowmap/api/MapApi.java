package com.caucraft.shadowmap.api;

import com.caucraft.shadowmap.api.map.MapChunk;
import com.caucraft.shadowmap.api.map.MapRegion;
import com.caucraft.shadowmap.api.storage.StorageAdapter;
import com.caucraft.shadowmap.api.storage.StorageKey;
import com.caucraft.shadowmap.api.ui.FullscreenMapEventHandler;
import com.caucraft.shadowmap.api.ui.MapDecorator;

/**
 * The main access point for registering event handlers and interacting with
 * ShadowMap.
 */
public interface MapApi {
    /**
     * A way to get a "current-enough" time with less performance impact when
     * called repeatedly than {@link System#currentTimeMillis()}.
     * @return the time in milliseconds at the end of the last client tick (or
     * more precisely, the time at which ShadowMap's {@link net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents#END_CLIENT_TICK} handler was invoked.
     */
    long getLastTickTime();

    <RegionType extends MapRegion<ChunkType, ChunkNbtContext>,
            ChunkType extends MapChunk<ChunkNbtContext>,
            ChunkNbtContext>
    StorageKey<RegionType> registerStorageAdapter(StorageAdapter<RegionType, ChunkType, ChunkNbtContext> storageAdapter);
    void registerFullscreenMapListener(FullscreenMapEventHandler eventListener);
    void registerMinimapDecorator(MapDecorator mapDecorator);
    void registerFullscreenMapDecorator(MapDecorator mapDecorator);
//    void registerRenderLayer(MapRenderLayer renderLayer); TODO implement me!
}
