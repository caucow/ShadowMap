package com.caucraft.shadowmap.client.map;

import com.caucraft.shadowmap.api.map.CeilingType;
import com.caucraft.shadowmap.api.map.MapChunk;
import com.caucraft.shadowmap.api.util.ChunkCache;
import com.caucraft.shadowmap.api.util.MapBlockState;
import com.caucraft.shadowmap.api.util.MergeResult;
import com.caucraft.shadowmap.api.util.RegistryWrapper;
import com.caucraft.shadowmap.client.render.RegionRenderContextImpl;
import com.caucraft.shadowmap.client.util.MapBlockStateMutable;
import com.caucraft.shadowmap.client.util.MapUtils;
import com.caucraft.shadowmap.client.util.data.CompactIntArray;
import com.caucraft.shadowmap.client.util.data.PaletteMap;
import com.caucraft.shadowmap.client.util.data.PaletteStorage;
import com.caucraft.shadowmap.client.util.sim.SingleBlockWorld;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.dimension.DimensionType;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class BlocksChunk extends MapChunk<BlocksNbtContext> {
    public static final int HEIGHT_BITS = BlockPos.SIZE_BITS_Y;
    public static final int HEIGHT_OFFSET = DimensionType.MIN_HEIGHT;
    public static final int HEIGHT_MASK = (1 << HEIGHT_BITS) - 1;
    public static final int LIGHT_BITS = 4;
    public static final int LIGHT_MASK = ((1 << LIGHT_BITS) - 1) << HEIGHT_BITS;
    public static final int OFF_TRANSPARENT = 256, OFF_LIQUID = 512;
    public static final int[] AO_SIMULATED_SHADE = { 0, -24, -48, -68, -84, -96, -105, -111, -114 };
    public static int[][] SURFACE_SLOPE_POINTS;
    public static int SURFACE_SLOPE_MAX;
    public static int[][] CAVE_SLOPE_POINTS;
    public static int CAVE_SLOPE_MAX;

    static {
        SURFACE_SLOPE_MAX = 128;
        SURFACE_SLOPE_POINTS = new int[][] {
                {6, -1, -1},
                {16, -1, 0},
                {8, -1, 1},
                {12, 0, -1},
                {12, 0, 1},
                {8, 1, -1},
                {16, 1, 0},
                {6, 1, 1},
        };
        CAVE_SLOPE_MAX = 24;
        CAVE_SLOPE_POINTS = new int[][] {
                {4, -1, -1},
                {12, -1, 0},
                {6, -1, 1},
                {10, 0, -1},
                {10, 0, 1},
                {6, 1, -1},
                {12, 1, 0},
                {4, 1, 1},
        };
    }

    private final AtomicInteger flags;
    private PaletteStorage<Biome> biomes;
    private PaletteStorage<BlockState> blocks;
    private CompactIntArray heightAndLight;

    /*
    TODO: Re-implement lighting.
        While testing api storage, save times were still very high for a test
        world without block ticking or mob griefing. As it turns out, lighting
        is similar to biomes in that it's not accurate for a chunk until all
        neighboring chunks' light sources are loaded. This has been causing TONS
        of unnecessary chunk updates and region full-saves where none was
        needed. As such, light level is still captured by the update method, but
        will not be included in has-changed logic until it can be reliably
        captured from the world and the renderer is refactored to make use of it.
     */

    public BlocksChunk() {
        this.flags = new AtomicInteger();
        this.biomes = new PaletteStorage<>(256, Biome.class);
        this.blocks = new PaletteStorage<>(256 * 3, BlockState.class);
        this.heightAndLight = new CompactIntArray(HEIGHT_BITS + LIGHT_BITS, 256 * 3);
    }

    @Override
    public int estimateMemoryUsage() {
        int usage = super.estimateMemoryUsage() + 20;
        if (biomes != null) {
            usage += biomes.estimateMemoryUsage();
        }
        if (blocks != null) {
            usage += blocks.estimateMemoryUsage();
        }
        if (heightAndLight != null) {
            usage += heightAndLight.estimateMemoryUsage();
        }
        return usage;
    }

    private void clearFlag(Flags flag) {
        flags.updateAndGet((value) -> value & ~flag.flag);
    }

    private void setFlag(Flags flag) {
        flags.updateAndGet((value) -> value | flag.flag);
    }

    private boolean isFlagSet(Flags flag) {
        return (flags.get() & flag.flag) != 0;
    }

    public Biome getBiome(int blockX, int blockZ) {
        return biomes.get(getBlockIndex(blockX, blockZ));
    }

    public void setBiome(int blockX, int blockZ, Biome biome) {
        biomes.set(getBlockIndex(blockX, blockZ), biome);
    }

    public BlockState getBlock(BlockType blockType, int blockX, int blockZ) {
        return blocks.get(getBlockIndex(blockX, blockZ) + blockType.offset);
    }

    public int getHeight(BlockType blockType, int blockX, int blockZ) {
        return (heightAndLight.get(getBlockIndex(blockX, blockZ) + blockType.offset) & HEIGHT_MASK) + HEIGHT_OFFSET;
    }

    public int getLight(BlockType blockType, int blockX, int blockZ) {
        return (heightAndLight.get(getBlockIndex(blockX, blockZ) + blockType.offset) & LIGHT_MASK) >> HEIGHT_BITS;
    }

    int getHeight(int index) {
        return (heightAndLight.get(index) & HEIGHT_MASK) + HEIGHT_OFFSET;
    }

    int getLight(int index) {
        return (heightAndLight.get(index) & LIGHT_MASK) >> HEIGHT_BITS;
    }

    public void setBlock(BlockType blockType, int blockX, int blockZ, BlockState block, int height, int light) {
        int index = getBlockIndex(blockX, blockZ) + blockType.offset;
        blocks.set(index, block);
        setHeightAndLight(index, height, light);
    }

    public void setHeight(int index, int height) {
        heightAndLight.set(index, heightAndLight.get(index) & LIGHT_MASK | (height - HEIGHT_OFFSET) & HEIGHT_MASK);
    }

    public void setLight(int index, int light) {
        heightAndLight.set(index, heightAndLight.get(index) & HEIGHT_MASK | light << HEIGHT_BITS & LIGHT_MASK);
    }

    public void setHeightAndLight(int index, int height, int light) {
        heightAndLight.set(index, (height - HEIGHT_OFFSET) & HEIGHT_MASK | light << HEIGHT_BITS & LIGHT_MASK);
    }

    @Override
    public boolean updateChunk(World world, Chunk chunk, ChunkCache chunkCache, CeilingType ceilingType,
            long curTimeMs) {
        boolean changed = false;

        ChunkSection topNonEmptySection = chunk.getHighestNonEmptySection();
        int bottomY = world.getBottomY();
        int topY = topNonEmptySection == null ? bottomY - 1 : topNonEmptySection.getYOffset() + 16;

        ChunkPos chunkPos = chunk.getPos();
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                changed |= update(
                        world, chunk, chunkCache, ceilingType,
                        chunkPos.getStartX() + localX,
                        chunkPos.getStartZ() + localZ,
                        bottomY, topY,
                        new BlockPos.Mutable(),
                        new BlockPos.Mutable()
                );
            }
        }

        return changed;
    }

    @Override
    public boolean updateSurroundedChunk(World world, Chunk chunk, ChunkCache chunkCache,
            CeilingType ceilingType, long curTimeMs) {
        // TODO should there be a biome check here? Updates are pretty fast anyways.
//        if (isFlagSet(RegionFlags.HAS_CURRENT_BIOMES) || !chunkCache.canProvideBiomes()) {
//            return false;
//        }
        // But then again, this update is *currently* called for chunks regardless of whether it has already been called
        // for them. I.e. a chunk in the middle of 8 other chunks will... only actually be updated at the moment all 8
        // surrounding chunks are loaded... idk there could still be a lot of render distance spam, esp. if player is
        // running in circles/reloading the chunks a lot.
        if (!chunkCache.canProvideBiomes()) {
            return false;
        }
        // Maybe in the future, only store the current-biome flags in memory, so stuff from disk can have one in-memory
        // update as chunks load to make sure. Also add flagging to Mojang chunks to prevent render distance reload spam

        boolean changed = false;
        int bottomY = world.getBottomY();
        ChunkPos chunkPos = chunk.getPos();
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                changed |= updateBiomes(chunkCache, chunkPos.getStartX() + localX,
                        chunkPos.getStartZ() + localZ,
                        bottomY,
                        new BlockPos.Mutable()
                );
            }
        }

        setFlag(Flags.HAS_CURRENT_BIOMES);
        setFlag(Flags.HAS_BIOMES);

        return changed;
    }

    @Override
    public boolean updateBlock(World world, Chunk chunk, ChunkCache chunkCache, CeilingType ceilingType, BlockPos pos,
            BlockState state, long curTimeMs) {
        ChunkSection topNonEmptySection = chunk.getHighestNonEmptySection();
        int bottomY = world.getBottomY();
        int topY = topNonEmptySection == null ? bottomY - 1 : topNonEmptySection.getYOffset() + 16;

        return update(world, chunk, chunkCache, ceilingType, pos.getX(), pos.getZ(), bottomY, topY, new BlockPos.Mutable(), new BlockPos.Mutable());
    }

    private boolean update(World world, Chunk chunk, ChunkCache chunkCache, CeilingType ceilingType, int worldX, int worldZ, int minY, int maxY, BlockPos.Mutable pos, BlockPos.Mutable posPlusOne) {
        pos.set(worldX, maxY, worldZ);
        posPlusOne.set(pos).setY(maxY + 1);

        int index = getBlockIndex(worldX, worldZ);
        boolean needSolid = true;
        boolean needTransparent = true;
        boolean needLiquid = true;
        int oldHighestBiome = Math.max(getHeight(index), Math.max(getHeight(index + OFF_TRANSPARENT), getHeight(index + OFF_LIQUID)));
        boolean changedLiquid = false;
        boolean changedSolid = false;
        BlockState bedrockState = null;
        int bedrockHeight = 0;
        int bedrockLight = 0;
        boolean changedTransparent = false;
        boolean changedBiome = false;

        int y = maxY;

        if (ceilingType == CeilingType.ROOFED) {
            // find first non-air block
            BlockState topBlock;
            for (; (topBlock = chunk.getBlockState(pos.setY(y))).isAir() && y >= minY; y--) {}

            // if bedrock, and if at the top of a chunk section, scan through it for non-opaque
            if (topBlock.getBlock() == Blocks.BEDROCK && (y == -1 || y > 0 && Integer.bitCount(y + 1) == 1 || y < 0 && Integer.bitCount(-y - 1) == 1)) {
                posPlusOne.setY(y + 1);
                bedrockState = topBlock;
                bedrockHeight = y;
                bedrockLight = world.getLightLevel(LightType.BLOCK, posPlusOne);

                for (; y >= minY; y--) {
                    pos.setY(y);
                    topBlock = chunk.getBlockState(pos);
                    MapBlockStateMutable mapState = (MapBlockStateMutable) topBlock;
                    if (!mapState.shadowMap$isOpacitySet()) {
                        MapUtils.updateOpacity(topBlock);
                    }
                    if (!mapState.shadowMap$isOpaque()) {
                        break;
                    }
                }
            }
        }

        for (; y >= minY && needSolid; y--) {
            pos.setY(y);
            posPlusOne.setY(y + 1);
            BlockState newBlock = chunk.getBlockState(pos);
            FluidState newFluid = newBlock.getFluidState();
            if (newBlock.isAir()) {
                continue;
            }

            if (needLiquid && !newFluid.isEmpty()) { // is the condition isAir() or newFluid != Fluids.EMPTY?
                int subIndex = index + OFF_LIQUID;
                BlockState oldBlock = blocks.get(subIndex);
                blocks.set(subIndex, newBlock);
                int oldHeight = getHeight(subIndex);
                setHeight(subIndex, y);
//                int oldLight = getLight(subIndex);
                int newLight = world.getLightLevel(LightType.BLOCK, posPlusOne);
                setLight(subIndex, newLight);
                needLiquid = false;
                changedLiquid = (oldBlock != newBlock) | (oldHeight != y);// | (oldLight != newLight);
            }
            MapBlockStateMutable mapState = (MapBlockStateMutable) newBlock;
            if (!mapState.shadowMap$isOpacitySet()) {
                MapUtils.updateOpacity(newBlock);
            }
            if (mapState.shadowMap$isOpaque()) {
                BlockState oldBlock = blocks.get(index);
                blocks.set(index, newBlock);
                int oldHeight = getHeight(index);
                setHeight(index, y);
//                int oldLight = getLight(index);
                int newLight = world.getLightLevel(LightType.BLOCK, posPlusOne);
                setLight(index, newLight);
                needSolid = false;
                changedSolid = (oldBlock != newBlock) | (oldHeight != y);// | (oldLight != newLight);
            } else if (needTransparent && (newFluid.isEmpty() || newBlock.getBlock() != newFluid.getBlockState().getBlock())) {
                int subIndex = index + OFF_TRANSPARENT;
                BlockState oldBlock = blocks.get(subIndex);
                blocks.set(subIndex, newBlock);
                int oldHeight = getHeight(subIndex);
                setHeight(subIndex, y);
//                int oldLight = getLight(subIndex);
                int newLight = world.getLightLevel(LightType.BLOCK, posPlusOne);
                setLight(subIndex, newLight);
                needTransparent = false;
                changedTransparent = (oldBlock != newBlock) | (oldHeight != y);// | (oldLight != newLight);
            }
        }

//        if (needSolid && bedrockState != null) {
//            BlockState oldBlock = blocks.get(index);
//            int oldHeight = getHeight(index);
////            int oldLight = getLight(index);
//            blocks.set(index, bedrockState);
//            setHeight(index, bedrockHeight);
////            setLight(index, bedrockLight);
//            needSolid = false;
//            changedSolid = (oldBlock != bedrockState) | (oldHeight != bedrockHeight);// | (oldLight != bedrockLight);
//        }
        if (needSolid) {
            changedSolid = blocks.get(index) != null;
            blocks.set(index, null);
            setHeight(index, minY);
            setLight(index, 0);
        }
        if (needTransparent) {
            int subIndex = index + OFF_TRANSPARENT;
            changedTransparent = blocks.get(subIndex) != null;
            blocks.set(subIndex, null);
            setHeight(subIndex, minY);
            setLight(subIndex, 0);
        }
        if (needLiquid) {
            int subIndex = index + OFF_LIQUID;
            changedLiquid = blocks.get(subIndex) != null;
            blocks.set(subIndex, null);
            setHeight(subIndex, minY);
            setLight(subIndex, 0);
        }
        int highestBiome = Math.max(getHeight(index), Math.max(getHeight(index + OFF_TRANSPARENT), getHeight(index + OFF_LIQUID)));
        if (needSolid & needTransparent & needLiquid) {
            Biome oldBiome = biomes.get(index);
            biomes.set(index, null);
            changedBiome = oldBiome != null;
        } else if (highestBiome != oldHighestBiome) {
            if (chunkCache.canProvideBiomes()) {
                Biome oldBiome = biomes.get(index);
                Biome newBiome = chunkCache.getBiome(pos.setY(highestBiome));
                biomes.set(index, newBiome);
                changedBiome = oldBiome != newBiome;
            } else {
                clearFlag(Flags.HAS_CURRENT_BIOMES);
            }
        }

        return changedSolid | changedTransparent | changedLiquid | changedBiome;
    }

    private boolean updateBiomes(ChunkCache chunkCache, int worldX, int worldZ, int minY, BlockPos.Mutable pos) {
        pos.setX(worldX).setZ(worldZ);

        int index = getBlockIndex(worldX, worldZ);
        int highestBiome = Math.max(getHeight(index), Math.max(getHeight(index + OFF_TRANSPARENT), getHeight(index + OFF_LIQUID)));
        if (highestBiome < minY) {
            Biome oldBiome = biomes.get(index);
            biomes.set(index, null);
            return oldBiome != null;
        } else if (chunkCache.canProvideBiomes()) {
            Biome oldBiome = biomes.get(index);
            Biome newBiome = chunkCache.getBiome(pos.setY(highestBiome));
            biomes.set(index, newBiome);
            return oldBiome != newBiome;
        } else {
            clearFlag(Flags.HAS_CURRENT_BIOMES);
        }

        return false;
    }

    void render(RegionRenderContextImpl renderContext) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                renderBlock(renderContext, x, z);
            }
        }
    }

    private void renderBlock(RegionRenderContextImpl renderContext, int x, int z) {
        HeightLightCache cache = renderContext.heightLightCache();
        int[] argbBuffer = renderContext.argbBuffer;

        int index = getBlockIndex(x, z);
        int transparentIndex = index + OFF_TRANSPARENT;
        int liquidIndex = index + OFF_LIQUID;
        SingleBlockWorld simWorld = MapUtils.EMPTY_WORLD1.get();

        BlockState solid = blocks.get(index);
        BlockState transparent = blocks.get(index + BlockType.TRANSPARENT.offset);
        BlockState liquid = blocks.get(index + BlockType.LIQUID.offset);
        Biome biome = biomes.get(index);

        int solidHeight = cache.getHeight(BlockType.OPAQUE, x, z);
        int transparentHeight = cache.getHeight(BlockType.TRANSPARENT, x, z);
        int liquidHeight = cache.getHeight(BlockType.LIQUID, x, z);
//        int solidLight = 255;
//        int transparentLight = 255;
//        int liquidLight = 255;
        BlockState lower, upper;
        int lowerHeight, upperHeight;
        BlockType lowerType, upperType;
//        int lowerLight, upperLight;

        if (liquid != null) {
            liquid = liquid.getFluidState().getBlockState();
        }
        if (transparent == null) {
            lower = liquid;
            lowerHeight = liquidHeight;
//            lowerLight = liquidLight;
            lowerType = BlockType.LIQUID;
            upper = null;
            upperHeight = Integer.MIN_VALUE;
//            upperLight = 0;
            upperType = null;
        } else if (liquid == null) {
            lower = transparent;
            lowerHeight = transparentHeight;
//            lowerLight = transparentLight;
            lowerType = BlockType.TRANSPARENT;
            upper = null;
            upperHeight = Integer.MIN_VALUE;
//            upperLight = 0;
            upperType = null;
        } else if (getHeight(transparentIndex) > getHeight(liquidIndex)) {
            lower = liquid;
            lowerHeight = liquidHeight;
//            lowerLight = liquidLight;
            lowerType = BlockType.LIQUID;
            upper = transparent;
            upperHeight = transparentHeight;
//            upperLight = transparentLight;
            upperType = BlockType.TRANSPARENT;
        } else {
            // Liquid on same level as transparent should be rendered as "above"
            lower = transparent;
            lowerHeight = transparentHeight;
//            lowerLight = transparentLight;
            lowerType = BlockType.TRANSPARENT;
            upper = liquid;
            upperHeight = liquidHeight;
//            upperLight = liquidLight;
            upperType = BlockType.LIQUID;
        }
        int dstA = 0, dstR = 0, dstG = 0, dstB = 0;
        if (solid != null) {
            renderBlock(renderContext, argbBuffer, simWorld, biome, solid, x, solidHeight, z, cache, BlockType.OPAQUE);
            dstA = argbBuffer[0];
            dstR = argbBuffer[1];
            dstG = argbBuffer[2];
            dstB = argbBuffer[3];
//            dstR = dstR * solidLight / 255;
//            dstG = dstG * solidLight / 255;
//            dstB = dstB * solidLight / 255;
        }
        if (lower != null) {
            renderBlock(renderContext, argbBuffer, simWorld, biome, lower, x, lowerHeight, z, cache, lowerType);
            int srcA = argbBuffer[0];
            int srcR = argbBuffer[1];
            int srcG = argbBuffer[2];
            int srcB = argbBuffer[3];
//            srcR = srcR * lowerLight / 255;
//            srcG = srcG * lowerLight / 255;
//            srcB = srcB * lowerLight / 255;
            if (srcA != 0) {
                int invA = dstA * (255 - srcA);
                dstA = srcA * 255 + invA;
                dstR = (srcA * srcR * 255 + invA * dstR) / dstA;
                dstG = (srcA * srcG * 255 + invA * dstG) / dstA;
                dstB = (srcA * srcB * 255 + invA * dstB) / dstA;
                dstA /= 255;
            }
        }
        if (upper != null) {
            renderBlock(renderContext, argbBuffer, simWorld, biome, upper, x, upperHeight, z, cache, upperType);
            int srcA = argbBuffer[0];
            int srcR = argbBuffer[1];
            int srcG = argbBuffer[2];
            int srcB = argbBuffer[3];
//            srcR = srcR * upperLight / 255;
//            srcG = srcG * upperLight / 255;
//            srcB = srcB * upperLight / 255;
            if (srcA != 0) {
                int invA = dstA * (255 - srcA);
                dstA = srcA * 255 + invA;
                dstR = (srcA * srcR * 255 + invA * dstR) / dstA;
                dstG = (srcA * srcG * 255 + invA * dstG) / dstA;
                dstB = (srcA * srcB * 255 + invA * dstB) / dstA;
                dstA /= 255;
            }
        }
        dstA = 255;
        renderContext.setColor(x, z, dstA << 24 | dstR << 16 | dstG << 8 | dstB);
    }

    private void renderBlock(RegionRenderContextImpl renderContext, int[] argbBuffer, SingleBlockWorld simWorld,
            Biome biome, BlockState block, int localX, int y, int localZ, HeightLightCache cache, BlockType blockType) {
        int color = ((MapBlockState) block).shadowMap$getColorARGB();
        int a = color >>> 24 & 0xFF;
        int r = color >>> 16 & 0xFF;
        int g = color >>> 8 & 0xFF;
        int b = color & 0xFF;

        int slopeShade = getSlopeShade(renderContext.world.getCeilingType(), cache, localX, y, localZ, blockType);
        if (slopeShade >= 0) {
            slopeShade = 255 - slopeShade;
            r = 255 - ((255 - r) * slopeShade / 255);
            g = 255 - ((255 - g) * slopeShade / 255);
            b = 255 - ((255 - b) * slopeShade / 255);
        } else {
            slopeShade = 255 + slopeShade;
            r = r * slopeShade / 255;
            g = g * slopeShade / 255;
            b = b * slopeShade / 255;
        }

        if (((MapBlockState) block).shadowMap$isTinted() || block.getBlock() == Blocks.WATER) {
            int tintColor = MinecraftClient.getInstance().getBlockColors().getColor(block, simWorld.setBlockAndBiome(block, biome), BlockPos.ORIGIN, 0);
            r = (tintColor >> 16 & 255) * r / 255;
            g = (tintColor >> 8 & 255) * g / 255;
            b = (tintColor & 255) * b / 255;
        }
        argbBuffer[0] = a;
        argbBuffer[1] = r;
        argbBuffer[2] = g;
        argbBuffer[3] = b;
    }

    private int getSlopeShade(CeilingType ceilingType, HeightLightCache cache, int localX, int height, int localZ, BlockType blockType) {
        // Higher Neighbor for Simulated AO Shading
        int higher = getHigherBlocks(cache, localX, height, localZ, blockType);
        // Difference * Direction for Slope Shading.
        int slope;
        if (ceilingType == CeilingType.OPEN) {
            slope = getSlope(cache, localX, height, localZ, blockType, SURFACE_SLOPE_POINTS, SURFACE_SLOPE_MAX);
        } else {
            slope = getSlope(cache, localX, height, localZ, blockType, CAVE_SLOPE_POINTS, CAVE_SLOPE_MAX);
        }

//        return (slope * 5 + AO_SIMULATED_SHADE[higher]) / 6;
        return slope;
    }

    private int getHigherBlocks(HeightLightCache cache, int localX, int height, int localZ, BlockType blockType) {
        return (cache.getHighest(localX - 1, localZ, blockType) > height ? 1 : 0)
                + (cache.getHighest(localX + 1, localZ, blockType) > height ? 1 : 0)
                + (cache.getHighest(localX, localZ - 1, blockType) > height ? 1 : 0)
                + (cache.getHighest(localX, localZ + 1, blockType) > height ? 1 : 0)
                + (cache.getHighest(localX - 1, localZ - 1, blockType) > height ? 1 : 0)
                + (cache.getHighest(localX - 1, localZ + 1, blockType) > height ? 1 : 0)
                + (cache.getHighest(localX + 1, localZ - 1, blockType) > height ? 1 : 0)
                + (cache.getHighest(localX + 1, localZ + 1, blockType) > height ? 1 : 0);
    }

    private int getSlope(HeightLightCache cache, int localX, int height, int localZ, BlockType blockType, int[][] slopePoints, int maxSlope) {
        int ax = 0, az = 0, bx = 0, bz = 0, above = 0, below = 1;
        for (int i = slopePoints.length - 1; i >= 0; i--) {
            int[] point = slopePoints[i];
            int relativeHeight = cache.getHighest(localX + point[1], localZ + point[2], blockType) - height;
            if (relativeHeight > 0) {
                above++;
                ax += point[0] * point[1] * relativeHeight;
                az += point[0] * point[2] * relativeHeight;
            } else {
                below++;
                bx += point[0] * point[1] * relativeHeight;
                bz += point[0] * point[2] * relativeHeight;
            }
        }
        int x = ax + bx / 32;
        int z = az + bz / 32;

        return MathHelper.clamp((x + x + z) * 2 / 3, -maxSlope, maxSlope);
    }

    @Override
    public void loadFromNbt(NbtCompound root, BlocksNbtContext contextMetadata) throws IOException {
        super.loadFromNbt(root, contextMetadata);

        if (root.contains("biomes", NbtElement.COMPOUND_TYPE)) {
            PaletteMap<Biome> biomePalette = contextMetadata.getBiomePalette();
            biomes.loadNbt(root.getCompound("biomes"), biomePalette::getValue);
        }

        if (root.contains("blocks", NbtElement.COMPOUND_TYPE)) {
            PaletteMap<BlockState> blockPalette = contextMetadata.getBlockPalette();
            blocks.loadNbt(root.getCompound("blocks"), blockPalette::getValue);
        }

        if (root.contains("heightAndLight", NbtElement.COMPOUND_TYPE)) {
            NbtCompound heightAndLightNbt = root.getCompound("heightAndLight");
            heightAndLight.loadNbt(heightAndLightNbt);
        }

        NbtList flagsList = root.getList("flags", NbtElement.STRING_TYPE);
        int newFlags = 0;
        for (NbtElement element : flagsList) {
            if (element instanceof NbtString flag) {
                try {
                    newFlags |= Flags.valueOf(flag.asString()).flag;
                } catch (IllegalArgumentException ignore) {}
            }
        }
        flags.set(newFlags);
    }

    @Override
    public NbtCompound saveToNbt(BlocksNbtContext contextMetadata) {
        PaletteMap<Biome> biomePalette = contextMetadata.getBiomePalette();
        RegistryWrapper<Biome> biomeRegistry = contextMetadata.getBiomeRegistry();

        NbtCompound biomesNbt = biomes.toNbt((Biome biome) -> {
            Identifier biomeId = biome == null ? null : biomeRegistry.getId(biome);
            if (biomeId == null) {
                return 0;
            }
            return biomePalette.registerId(biomeId::toString, biome);
        });

        PaletteMap<BlockState> blockPalette = contextMetadata.getBlockPalette();

        StringBuilder sb = new StringBuilder();
        NbtCompound blocksNbt = blocks.toNbt((BlockState block) -> {
            if (block == null) {
                return 0;
            }
            return blockPalette.registerId(() -> {
                sb.setLength(0);
                MapUtils.blockStateToString(sb, block);
                return sb.toString();
            }, block);
        });

        NbtCompound heightAndLightNbt = heightAndLight.toNbt();

        NbtList flagsList = new NbtList();
        for (Flags flag : Flags.values()) {
            if (isFlagSet(flag)) {
                flagsList.add(NbtString.of(flag.name()));
            }
        }

        NbtCompound root = super.saveToNbt(contextMetadata);
        root.put("biomes", biomesNbt);
        root.put("blocks", blocksNbt);
        root.put("heightAndLight", heightAndLightNbt);
        root.put("flags", flagsList);
        return root;
    }

    @Override
    public MergeResult mergeFrom(MapChunk<BlocksNbtContext> o) {
        BlocksChunk other = (BlocksChunk) o;
        boolean thisIsOlder = getLastModified() < other.getLastModified();
        boolean blocksDiff = !blocks.equals(other.blocks);
        boolean heightAndLightDiff = !heightAndLight.equals(other.heightAndLight);
        MergeResult result = MergeResult.getResult();

        if (thisIsOlder) {
            boolean biomesDiff = !biomes.equals(other.biomes);
            if (blocksDiff) {
                blocks = other.blocks;
                result = result.usedOther();
            } else {
                result = result.usedThis();
            }
            if (heightAndLightDiff) {
                heightAndLight = other.heightAndLight;
                result = result.usedOther();
            } else {
                result = result.usedThis();
            }
            if ((heightAndLightDiff || !isFlagSet(Flags.HAS_BIOMES)) && other.isFlagSet(Flags.HAS_BIOMES)
                    || biomesDiff && other.isFlagSet(Flags.HAS_CURRENT_BIOMES)) {
                biomes = other.biomes;
                flags.updateAndGet((value) -> {
                    value = Flags.HAS_BIOMES.copyFromTo(other.flags.get(), value);
                    value = Flags.HAS_CURRENT_BIOMES.copyFromTo(other.flags.get(), value);
                    return value;
                });
                result = result.usedOther();
            } else {
                result = result.usedThis();
            }
        } else {
            if (!blocksDiff || !heightAndLightDiff) {
                result = result.usedOther();
            }
            if (blocksDiff || heightAndLightDiff) {
                result = result.usedThis();
            }
            if (!isFlagSet(Flags.HAS_BIOMES)
                    && other.isFlagSet(Flags.HAS_BIOMES)
                    || !heightAndLightDiff
                    && !isFlagSet(Flags.HAS_CURRENT_BIOMES)
                    && other.isFlagSet(Flags.HAS_CURRENT_BIOMES)) {
                biomes = other.biomes;
                flags.updateAndGet((value) -> {
                    value = Flags.HAS_BIOMES.copyFromTo(other.flags.get(), value);
                    value = Flags.HAS_CURRENT_BIOMES.copyFromTo(other.flags.get(), value);
                    return value;
                });
                result = result.usedOther();
            }
        }
        return result;
    }

    private static int getBlockIndex(int x, int z) {
        return (z & 0x0F) << 4 | (x & 0x0F);
    }

    /**
     * Since these flags may be saved to NBT, this enum is used to map values
     * saved to disk to the numerical flags in memory, which might change with
     * updates to the mod.
     */
    public enum Flags {
        HAS_BIOMES(),
        HAS_CURRENT_BIOMES();

        public final int flag;

        Flags() {
            this.flag = 1 << ordinal();
        }

        public int copyFromTo(int from, int to) {
            return to & ~flag | from & flag;
        }
    }

    public enum BlockType {
        OPAQUE(0),
        TRANSPARENT(OFF_TRANSPARENT),
        LIQUID(OFF_LIQUID);

        final int offset;

        BlockType(int offset) {
            this.offset = offset;
        }
    }

    // NOTE: This uses hard-coded index values instead of
    // getBlockIndex(int, int).
    public static class HeightLightCache {
        private final int[] center;
        /* Contents: {NW, N, NE, SW, S, SE, W, E} */
        private final int[] edge;

        public HeightLightCache(BlocksChunk centerChunk, BlocksChunk nw, BlocksChunk n, BlocksChunk ne, BlocksChunk w, BlocksChunk e, BlocksChunk sw, BlocksChunk s, BlocksChunk se) {
            center = new int[256 * 3];
            centerChunk.heightAndLight.get(0, 256 * 3, center, 0);
            edge = new int[68 * 3];
            if (nw != null) {
                edge[0] = nw.heightAndLight.get(255);
                edge[68] = nw.heightAndLight.get(255 + BlockType.TRANSPARENT.offset);
                edge[136] = nw.heightAndLight.get(255 + BlockType.LIQUID.offset);
            }
            if (n != null) {
                n.heightAndLight.get(240, 256, edge, 1);
                n.heightAndLight.get(240 + BlockType.TRANSPARENT.offset, 256 + BlockType.TRANSPARENT.offset, edge, 1 + 68);
                n.heightAndLight.get(240 + BlockType.LIQUID.offset, 256 + BlockType.LIQUID.offset, edge, 1 + 136);
            }
            if (ne != null) {
                edge[17] = ne.heightAndLight.get(240);
                edge[17 + 68] = ne.heightAndLight.get(240 + BlockType.TRANSPARENT.offset);
                edge[17 + 136] = ne.heightAndLight.get(240 + BlockType.LIQUID.offset);
            }
            if (w != null) {
                for (int dest = 36, src = 15; dest < 52; dest++, src += 16) {
                    edge[dest] = w.heightAndLight.get(src);
                    edge[dest + 68] = w.heightAndLight.get(src + BlockType.TRANSPARENT.offset);
                    edge[dest + 136] = w.heightAndLight.get(src + BlockType.LIQUID.offset);
                }
            }
            if (e != null) {
                for (int dest = 52, src = 0; dest < 68; dest++, src += 16) {
                    edge[dest] = e.heightAndLight.get(src);
                    edge[dest + 68] = e.heightAndLight.get(src + BlockType.TRANSPARENT.offset);
                    edge[dest + 136] = e.heightAndLight.get(src + BlockType.LIQUID.offset);
                }
            }
            if (sw != null) {
                edge[18] = sw.heightAndLight.get(15);
                edge[18 + 68] = sw.heightAndLight.get(15 + BlockType.TRANSPARENT.offset);
                edge[18 + 136] = sw.heightAndLight.get(15 + BlockType.LIQUID.offset);
            }
            if (s != null) {
                s.heightAndLight.get(0, 16, edge, 19);
                s.heightAndLight.get(BlockType.TRANSPARENT.offset, 16 + BlockType.TRANSPARENT.offset, edge, 19 + 68);
                s.heightAndLight.get(BlockType.LIQUID.offset, 16 + BlockType.LIQUID.offset, edge, 19 + 136);
            }
            if (se != null) {
                edge[35] = se.heightAndLight.get(0);
                edge[35 + 68] = se.heightAndLight.get(BlockType.TRANSPARENT.offset);
                edge[35 + 136] = se.heightAndLight.get(BlockType.LIQUID.offset);
            }
            // TODO remove these 2 loops and refactor to allow lighting
            for (int i = 0; i < center.length; i++) {
                center[i] = (center[i] & HEIGHT_MASK) + HEIGHT_OFFSET;
            }
            for (int i = 0; i < edge.length; i++) {
                edge[i] = (edge[i] & HEIGHT_MASK) + HEIGHT_OFFSET;
            }
        }

        public int getHeight(BlockType blockType, int relativeX, int relativeZ) {
            if (relativeX >= 0 && relativeZ >= 0 && relativeX < 16 && relativeZ < 16) {
                return center[getBlockIndex(relativeX, relativeZ) + blockType.offset];
            }
            int offset = switch (blockType) {
                case OPAQUE -> 0;
                case TRANSPARENT -> 68;
                case LIQUID -> 136;
            };
            if (relativeZ == -1) {
                offset += relativeX + 1;
            } else if (relativeZ == 16) {
                offset += relativeX + 19;
            } else if (relativeX == -1) {
                offset += relativeZ + 36;
            } else if (relativeX == 16) {
                offset += relativeZ + 52;
            }
            return edge[offset];
        }

        public int getHighest(int relativeX, int relativeZ) {
            if (relativeX >= 0 && relativeZ >= 0 && relativeX < 16 && relativeZ < 16) {
                int index = getBlockIndex(relativeX, relativeZ);
                int a = center[index];
                int b = center[index + BlockType.TRANSPARENT.offset];
                int c = center[index + BlockType.LIQUID.offset];
                return Math.max(Math.max(a, b), c);
            }
            int offset = 0;
            if (relativeZ == -1) {
                offset = relativeX + 1;
            } else if (relativeZ == 16) {
                offset = relativeX + 19;
            } else if (relativeX == -1) {
                offset = relativeZ + 36;
            } else if (relativeX == 16) {
                offset = relativeZ + 52;
            }
            int a = edge[offset];
            int b = edge[offset + 68];
            int c = edge[offset + 136];
            return Math.max(Math.max(a, b), c);
        }

        public int getHighest(int relativeX, int relativeZ, BlocksChunk.BlockType blockType) {
            if (relativeX >= 0 && relativeZ >= 0 && relativeX < 16 && relativeZ < 16) {
                int index = getBlockIndex(relativeX, relativeZ);
                if (blockType == BlockType.OPAQUE) {
                    return center[getBlockIndex(relativeX, relativeZ)];
                }
                int a = center[index];
                int b = center[index + blockType.offset];
                return Math.max(a, b);
            }
            int offset = 0;
            if (relativeZ == -1) {
                offset = relativeX + 1;
            } else if (relativeZ == 16) {
                offset = relativeX + 19;
            } else if (relativeX == -1) {
                offset = relativeZ + 36;
            } else if (relativeX == 16) {
                offset = relativeZ + 52;
            }
            return switch (blockType) {
                case OPAQUE -> edge[offset];
                case LIQUID -> Math.max(edge[offset], edge[offset + 136]);
                case TRANSPARENT -> Math.max(edge[offset], edge[offset + 68]);
            };
        }

        /**
         * Gets light from within this chunk only (Since blocks are at most one
         * pixel, light is not interpolated and thus needs no neighbor data.)
         */
        public int getLight(BlockType blockType, int blockX, int blockZ) {
            if (blockX >= 0 && blockZ >= 0 && blockX < 16 && blockZ < 16) {
                return (center[getBlockIndex(blockX, blockZ) + blockType.offset] & LIGHT_MASK) >>> HEIGHT_BITS;
            }
            return 0;
        }
    }
}
