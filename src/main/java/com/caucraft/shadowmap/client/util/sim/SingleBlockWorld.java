package com.caucraft.shadowmap.client.util.sim;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.BlockView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;
import org.jetbrains.annotations.Nullable;

public class SingleBlockWorld implements BlockView, BlockRenderView {
    private BlockState block;
    private Biome biome;

    public SingleBlockWorld setBlockAndBiome(BlockState block, Biome biome) {
        this.block = block;
        this.biome = biome;
        return this;
    }

    public SingleBlockWorld setBlock(BlockState block) {
        this.block = block;
        return this;
    }

    public SingleBlockWorld setBiome(Biome biome) {
        this.biome = biome;
        return this;
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return block;
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        if (block == null) {
            return null;
        }
        return block.getFluidState();
    }

    @Override
    public int getHeight() {
        return 16;
    }

    @Override
    public int getBottomY() {
        return 0;
    }

    @Override
    public float getBrightness(Direction direction, boolean shaded) {
        return 1.0F;
    }

    @Override
    public LightingProvider getLightingProvider() {
        return null;
    }

    @Override
    public int getColor(BlockPos pos, ColorResolver colorResolver) {
        return biome == null ? -1 : colorResolver.getColor(biome, 0, 0);
    }
}
