package com.caucraft.shadowmap.client.render;

import com.caucraft.shadowmap.client.map.BlocksChunk;
import com.caucraft.shadowmap.client.map.BlocksRegion;
import com.caucraft.shadowmap.client.map.MapWorldImpl;
import com.caucraft.shadowmap.client.map.RegionContainerImpl;

public class RegionRenderContextImpl {
    public final MapWorldImpl world;
    public final RegionContainerImpl region;

    /**
     * An int buffer containing combined ARGB values for an entire 512 x 512
     * region. Values are stored in row-major order indexed using
     * {@code [regionBlockZ << 9 | regionBlockX]}.
     */
    public final int[] imageBuffer;

    /**
     * A small 4-int buffer for calculating temporary separate ARGB values for a
     * single pixel.
     */
    public final int[] argbBuffer;

    private int chunkX, chunkZ, chunkBlockX, chunkBlockZ;
    private BlocksChunk.HeightLightCache heightLightCache;
    private final BlocksRegion blockLayer;
    private BlocksChunk chunk;

    public RegionRenderContextImpl(MapWorldImpl world, RegionContainerImpl region, BlocksRegion blockLayer, int[] imageBuffer, int[] argbBuffer) {
        this.world = world;
        this.region = region;
        this.imageBuffer = imageBuffer;
        this.argbBuffer = argbBuffer;

        this.blockLayer = blockLayer;
    }

    /**
     * Sets up the context to render the chunk. This method returns whether the
     * chunk exists and the context could be set up to render it. If the chunk
     * can not be rendered, also clears the chunk's color in the image buffer.
     * @param chunkX the region-relative chunk X.
     * @param chunkZ the region-relative chunk Z.
     * @return true if the chunk was not null and the region was able to be set
     * up to render it, false otherwise.
     */
    public boolean beginChunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.chunkBlockX = chunkX << 4;
        this.chunkBlockZ = chunkZ << 4;

        chunk = blockLayer.getChunk(chunkX, chunkZ, false);
        if (chunk == null) {
            for (int z = 0, zIndex = chunkZ << 13 | chunkX << 4; z < 16; z++, zIndex += 512) {
                for (int x = 0; x < 16; x++) {
                    imageBuffer[zIndex + x] = 0;
                }
            }
            return false;
        }
        heightLightCache = new BlocksChunk.HeightLightCache(
                chunk,
                blockLayer.getChunkInWorld(chunkX - 1, chunkZ - 1),
                blockLayer.getChunkInWorld(chunkX, chunkZ - 1),
                blockLayer.getChunkInWorld(chunkX + 1, chunkZ - 1),
                blockLayer.getChunkInWorld(chunkX - 1, chunkZ),
                blockLayer.getChunkInWorld(chunkX + 1, chunkZ),
                blockLayer.getChunkInWorld(chunkX - 1, chunkZ + 1),
                blockLayer.getChunkInWorld(chunkX, chunkZ + 1),
                blockLayer.getChunkInWorld(chunkX + 1, chunkZ + 1)
        );
        return true;
    }

    public BlocksChunk chunk() {
        return chunk;
    }

    public BlocksChunk.HeightLightCache heightLightCache() {
        return heightLightCache;
    }

    public void setColor(int chunkBlockX, int chunkBlockZ, int color) {
        imageBuffer[(this.chunkBlockZ | chunkBlockZ) << 9 | (this.chunkBlockX | chunkBlockX)] = color;
    }
}
