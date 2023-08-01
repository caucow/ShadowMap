package com.caucraft.shadowmap.api.map;

import com.caucraft.shadowmap.api.util.MergeResult;
import net.minecraft.nbt.NbtCompound;

import java.io.IOException;

/**
 * A map region that contains no chunk data.
 */
public abstract class ChunklessMapRegion extends MapRegion<ChunklessMapChunk, Void> {

    protected ChunklessMapRegion(RegionContainer regionContainer) {
        super(regionContainer);
    }

    @Override
    protected ChunklessMapChunk[] supplyChunkArray(int size) {
        return null;
    }

    @Override
    protected ChunklessMapChunk supplyChunk() {
        return null;
    }

    @Override
    protected Void supplyNbtContext() {
        return null;
    }

    @Override
    public MergeResult mergeFrom(MapRegion<ChunklessMapChunk, Void> other) {
        return MergeResult.getResult().usedThis();
    }

    @Override
    protected void loadNbtContext(NbtCompound contextRoot, Void ignore) throws IOException {}

    @Override
    protected NbtCompound saveNbtContext(Void ignore) {
        return null;
    }
}
