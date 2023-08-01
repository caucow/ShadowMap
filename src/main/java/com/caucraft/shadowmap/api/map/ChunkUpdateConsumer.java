package com.caucraft.shadowmap.api.map;

import com.caucraft.shadowmap.api.util.ChunkCache;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * Interface for objects that receive chunk and block updates.
 */
public interface ChunkUpdateConsumer {

    /**
     * Updates a map chunk from a Minecraft world and chunk.<br>
     * <br>
     * Multithreaded Implementation Note:<br>
     * This is called internally by ShadowMap after locking the region
     * containing the provided chunk. Calling this method should only be done in
     * rare cases and always be done by scheduling an update with the Map API.
     * TODO "scheduling such a modification with the Map API" needs to be added.
     * @param world the currently loaded Minecraft world.
     * @param chunk the Minecraft chunk that updated (likely due to just being
     * loaded).
     * @param adjChunkCache a cache of chunks adjacent to the updated chunk at
     * the time the update was scheduled.
     * @param ceilingType the assumed ceiling type of the world (likely based on
     * whether the world is in the nether or not).
     * @param curTimeMs the system time, in milliseconds, when the update was
     * scheduled.
     * @return true if the data in this region changed as a result of this chunk
     * update, false otherwise.
     */
    boolean updateChunk(World world, Chunk chunk, ChunkCache adjChunkCache, CeilingType ceilingType, long curTimeMs);

    /**
     * Updates a map chunk after a block update.<br>
     * <br>
     * Multithreaded Implementation Note:<br>
     * This is called internally by ShadowMap after locking the region
     * containing the provided chunk. Calling this method should only be done in
     * rare cases and always be done by scheduling an update with the Map API.
     * TODO "scheduling such a modification with the Map API" needs to be added.
     * @param world the currently loaded Minecraft world.
     * @param chunk the Minecraft chunk containing the block update.
     * @param adjChunkCache a cache of chunks adjacent to the updated chunk at
     * the time the update was scheduled.
     * @param ceilingType the assumed ceiling type of the world (likely based on
     * whether the world is in the nether or not).
     * @param pos the location of the updated block.
     * @param state the new {@link BlockState} of the updated block.
     * @param curTimeMs the system time, in milliseconds, when the update was
     * scheduled.
     * @return true if the data in this region changed as a result of this chunk
     * update, false otherwise.
     */
    boolean updateBlock(World world, Chunk chunk, ChunkCache adjChunkCache, CeilingType ceilingType, BlockPos pos,
            BlockState state, long curTimeMs);

    /**
     * Updates a "surrounded" map chunk from a Minecraft world and chunk. This
     * is called for the provided chunk when all 8 of its neighboring chunks are
     * loaded.<br>
     * <br>
     * This exists primarily to get biome data for specific blocks, since this
     * is only possible when all adjacent chunks are loaded. For almost every
     * other case, {@link #updateChunk(World, Chunk, ChunkCache, CeilingType, long)}
     * should be preferred.<br>
     * <br>
     * Multithreaded Implementation Note:<br>
     * This is called internally by ShadowMap after locking the region
     * containing the provided chunk. Calling this method should only be done in
     * rare cases and always be done by scheduling an update with the Map API.
     * TODO "scheduling such a modification with the Map API" needs to be added.
     * @param world the currently loaded Minecraft world.
     * @param chunk the Minecraft chunk that updated (likely due to just being
     * loaded).
     * @param adjChunkCache a cache of chunks adjacent to the updated chunk at
     * the time the update was scheduled.
     * @param ceilingType the assumed ceiling type of the world (likely based on
     * whether the world is in the nether or not).
     * @param curTimeMs the system time, in milliseconds, when the update was
     * scheduled.
     * @return true if the data in this region changed as a result of this chunk
     * update, false otherwise.
     */
    boolean updateSurroundedChunk(World world, Chunk chunk, ChunkCache adjChunkCache, CeilingType ceilingType,
            long curTimeMs);
}
