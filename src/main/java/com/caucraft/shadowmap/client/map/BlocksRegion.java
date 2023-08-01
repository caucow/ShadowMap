package com.caucraft.shadowmap.client.map;

import com.caucraft.shadowmap.api.map.MapRegion;
import com.caucraft.shadowmap.api.map.RegionContainer;
import net.minecraft.nbt.NbtCompound;

import java.io.IOException;

public class BlocksRegion extends MapRegion<BlocksChunk, BlocksNbtContext> {

    public BlocksRegion(RegionContainerImpl region) {
        super(region);
    }

    public BlocksChunk getChunkInWorld(int regionChunkX, int regionChunkZ) {
        int rx = regionChunkX >> 5;
        int rz = regionChunkZ >> 5;
        if (rx == 0 && rz == 0) {
            return getChunk(regionChunkX, regionChunkZ, false);
        }
        RegionContainer myRegion = regionContainer;
        RegionContainer otherRegion = myRegion.getWorld().getRegion(myRegion.getRegionX() + rx, myRegion.getRegionZ() + rz, false, false);
        if (otherRegion == null) {
            return null;
        }
        BlocksRegion otherLayer = ((RegionContainerImpl) otherRegion).getBlocks();
        if (otherLayer == null) {
            return null;
        }
        return otherLayer.getChunk(regionChunkX, regionChunkZ, false);
    }

    @Override
    protected BlocksChunk[] supplyChunkArray(int size) {
        return new BlocksChunk[size];
    }

    @Override
    protected BlocksChunk supplyChunk() {
        return new BlocksChunk();
    }

    @Override
    protected BlocksNbtContext supplyNbtContext() {
        return new BlocksNbtContext(regionContainer.getWorld().getBiomeRegistry(), regionContainer.getWorld().getBlockRegistry());
    }

    @Override
    protected void loadNbtContext(NbtCompound contextRoot, BlocksNbtContext blocksNbtContext) throws IOException {
        blocksNbtContext.loadFromNbt(contextRoot);
    }

    @Override
    protected NbtCompound saveNbtContext(BlocksNbtContext blocksNbtContext) {
        return blocksNbtContext.saveToNbt();
    }

}
