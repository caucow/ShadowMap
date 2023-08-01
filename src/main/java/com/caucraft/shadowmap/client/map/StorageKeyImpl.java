package com.caucraft.shadowmap.client.map;

import com.caucraft.shadowmap.api.map.MapChunk;
import com.caucraft.shadowmap.api.map.MapRegion;
import com.caucraft.shadowmap.api.map.RegionContainer;
import com.caucraft.shadowmap.api.storage.StorageAdapter;

public class StorageKeyImpl<
        RegionType extends MapRegion<ChunkType, ChunkNbtContext>,
        ChunkType extends MapChunk<ChunkNbtContext>,
        ChunkNbtContext>
        implements com.caucraft.shadowmap.api.storage.StorageKey<RegionType> {

    public final StorageAdapter<RegionType, ChunkType, ChunkNbtContext> storageAdapter;
    public final int key;

    public StorageKeyImpl(StorageAdapter<RegionType, ChunkType, ChunkNbtContext> storageAdapter, int key) {
        this.storageAdapter = storageAdapter;
        this.key = key;
    }

    @Override
    public int keyId() {
        return key;
    }

    @Override
    public RegionType createStorage(RegionContainer regionContainer) {
        return storageAdapter.supplyRegion(regionContainer);
    }
}
