package com.caucraft.shadowmap.api.storage;

import com.caucraft.shadowmap.api.map.MapChunk;
import com.caucraft.shadowmap.api.map.MapRegion;
import com.caucraft.shadowmap.api.map.RegionContainer;

/**
 * Storage layer for capturing and storing chunk metadata directly in the map
 * region files.
 * @param <RegionType> ImportType of {@link MapRegion} that will be accessible using
 * this StorageAdapter.
 */
public interface StorageAdapter<
        RegionType extends MapRegion<ChunkType, ChunkNbtContext>,
        ChunkType extends MapChunk<ChunkNbtContext>,
        ChunkNbtContext> {
    RegionType supplyRegion(RegionContainer container);
}
