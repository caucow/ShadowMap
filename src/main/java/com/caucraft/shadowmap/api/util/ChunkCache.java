package com.caucraft.shadowmap.api.util;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

/**
 * A cache used during a chunk or block update containing all chunks adjacent to
 * the updated chunk at the moment of the update. Used to ensure neighboring
 * chunks are available on update threads even if they have been unloaded on the
 * main thread after scheduling an update.
 */
public class ChunkCache implements BiomeAccess.Storage {
    private final BiomeAccess biomeAccess;
    private final ChunkPos midPos;
    private final Chunk[] chunks;
    private final int chunksPresent;

    public ChunkCache(WorldChunk middleChunk) {
        World world = middleChunk.getWorld();

        this.biomeAccess = world.getBiomeAccess().withSource(this);
        ChunkPos midPos = this.midPos = middleChunk.getPos();
        chunks = new Chunk[] {
                world.getChunk(midPos.x - 1, midPos.z - 1, ChunkStatus.BIOMES, false),
                world.getChunk(midPos.x    , midPos.z - 1, ChunkStatus.BIOMES, false),
                world.getChunk(midPos.x + 1, midPos.z - 1, ChunkStatus.BIOMES, false),
                world.getChunk(midPos.x - 1, midPos.z    , ChunkStatus.BIOMES, false),
                middleChunk,
                world.getChunk(midPos.x + 1, midPos.z    , ChunkStatus.BIOMES, false),
                world.getChunk(midPos.x - 1, midPos.z + 1, ChunkStatus.BIOMES, false),
                world.getChunk(midPos.x    , midPos.z + 1, ChunkStatus.BIOMES, false),
                world.getChunk(midPos.x + 1, midPos.z + 1, ChunkStatus.BIOMES, false)
        };
        int present = 0;
        for (int i = 0; i < 9; i++) {
            if (chunks[i] != null) {
                present |= 1 << i;
            }
        }
        this.chunksPresent = present;
    }

    /**
     * Gets an adjacent chunk in the provided relative direction. (~0, ~0) would
     * return the chunk that received an update.
     * @param relativeX relative X coordinate of adjacent chunk, from -1 to 1.
     * @param relativeZ relative Z coordinate of adjacent chunk, from -1 to 1.
     * @return the adjacent chunk, if one was present at the time an update was
     * scheduled, or null if no chunk was present.
     */
    public Chunk getAdjacentChunk(int relativeX, int relativeZ) {
        if (relativeX < -1 || relativeZ < -1 || relativeX > 1 || relativeZ > 1) {
            throw new IllegalArgumentException("Cannot access non-adjacent chunk at ~" + relativeX + ", ~" + relativeZ);
        }
        return chunks[3 * (relativeZ + 1) + relativeX + 1];
    }

    /**
     * @return true if it is safe to read biome data at a specific block using
     * this cache, generally meaning the 3x3 of chunks around the updated chunk
     * are all cached.
     */
    public boolean canProvideBiomes() {
        return chunksPresent == 0x1FF;
    }

    /**
     * If {@link #canProvideBiomes()} returns true, this can be used to get the
     * biome present at a specific block. If {@link #canProvideBiomes()} does
     * not return true and a block's biome is needed, a flag should be set that
     * biome information is outdated, and biome information can be synchronized
     * during the next "surrounded-chunk" update.
     * @param pos the location of the block to get a biome for. This must be
     * within the middle chunk of this cache.
     * @return the biome at the specified block's location.
     * @throws IllegalStateException if one of the adjacent chunks is not
     * cached.
     * @throws IllegalArgumentException if this method is called with a block
     * position outside the middle chunk.
     */
    public Biome getBiome(BlockPos pos) {
        if (!canProvideBiomes()) {
            throw new IllegalStateException("Tried to get biome at block when adjacent chunks are missing.");
        }
        if (midPos.getStartX() > pos.getX() || midPos.getEndX() < pos.getX() || midPos.getStartZ() > pos.getZ() || midPos.getEndZ() < pos.getZ()) {
            throw new IllegalArgumentException("Tried to get biome outside middle simulated chunk.");
        }
        return biomeAccess.getBiome(pos).value();
    }

    @Override
    public RegistryEntry<Biome> getBiomeForNoiseGen(int biomeX, int biomeY, int biomeZ) {
        int chunkX = BiomeCoords.toChunk(biomeX) - midPos.x + 1;
        int chunkZ = BiomeCoords.toChunk(biomeZ) - midPos.z + 1;
        return chunks[chunkZ * 3 + chunkX].getBiomeForNoiseGen(biomeX, biomeY, biomeZ);
    }
}
