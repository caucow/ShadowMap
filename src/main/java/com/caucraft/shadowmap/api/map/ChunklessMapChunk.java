package com.caucraft.shadowmap.api.map;

import com.caucraft.shadowmap.api.util.ChunkCache;
import com.caucraft.shadowmap.api.util.MergeResult;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.io.IOException;

/**
 * A chunk that contains no data and is not saved.
 */
public class ChunklessMapChunk extends MapChunk<Void> {

    @Override
    public boolean updateBlock(World world, Chunk chunk, ChunkCache adjChunkCache, CeilingType ceilingType,
            BlockPos pos, BlockState state, long curTimeMs) {
        return false;
    }

    @Override
    public boolean updateSurroundedChunk(World world, Chunk chunk, ChunkCache adjChunkCache, CeilingType ceilingType,
            long curTimeMs) {
        return false;
    }

    @Override
    public boolean updateChunk(World world, Chunk chunk, ChunkCache adjChunkCache, CeilingType ceilingType,
            long curTimeMs) {
        return false;
    }

    @Override
    public MergeResult mergeFrom(MapChunk<Void> other) {
        return MergeResult.getResult().usedThis();
    }

    @Override
    public void loadFromNbt(NbtCompound root, Void unused) throws IOException {}

    @Override
    public NbtCompound saveToNbt(Void unused) {
        return null;
    }

}
