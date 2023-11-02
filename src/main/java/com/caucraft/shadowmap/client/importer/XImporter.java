package com.caucraft.shadowmap.client.importer;

import com.caucraft.shadowmap.api.util.RegistryWrapper;
import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.map.BlocksChunk;
import com.caucraft.shadowmap.client.map.BlocksRegion;
import com.caucraft.shadowmap.client.map.MapManagerImpl;
import com.caucraft.shadowmap.client.util.MapBlockStateMutable;
import com.caucraft.shadowmap.client.util.MapUtils;
import com.caucraft.shadowmap.client.util.io.ByteBufferInputStream;
import com.caucraft.shadowmap.client.util.io.ByteBufferOutputStream;
import com.caucraft.shadowmap.client.waypoint.Waypoint;
import com.caucraft.shadowmap.client.waypoint.WaypointGroup;
import com.caucraft.shadowmap.client.waypoint.WorldWaypointManager;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.Registry;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.apache.commons.io.IOUtils;
import org.joml.Vector3d;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class XImporter {
    private static final int IS_FUCKING_GRASS_OR_WATER  = 0x0000_0001;
    private static final int HAS_LAYERS                 = 0x0000_0002;
    private static final int HAS_REDUNDANT_HEIGHT       = 0x0000_0004;
    private static final int HAS_EXTRA_INT              = 0x0000_0008;

    private static final int HAS_EXTRA_BYTE             = 0x0000_0010;

    private static final int LIGHT_MASK                 = 0x0000_0F00;
    private static final int LIGHT_SHIFT                = 8;
    private static final int HEIGHT1_MASK               = 0x000F_F000;
    private static final int HEIGHT1_SHIFT              = 12;

    private static final int HAS_BIOME                  = 0x0010_0000;
    private static final int PALETTE_BLOCK              = 0x0020_0000;
    private static final int PALETTE_BIOME              = 0x0040_0000;
    private static final int IS_OLD_BIOME               = 0x0080_0000;

    private static final int HAS_MORE_REDUNDANT_HEIGHT  = 0x0100_0000;
    private static final int HEIGHT2_MASK               = 0x1E00_0000;
    private static final int HEIGHT2_SHIFT              = 17;

    private static final int LAYER_LIGHT_MASK           = 0x0000_00F0;
    private static final int LAYER_LIGHT_SHIFT          = 4;
    private static final int LAYER_PALETTE_BLOCK        = 0x0000_0400;

    public static boolean importWaypointsFromX(File xWpFile, MapManagerImpl mapManager, WorldWaypointManager wpManager,
            Map<String, WaypointGroup> groupMap, List<Waypoint> pointList, List<String> groupNameList,
            Set<UUID> deathPointSet) {
        ByteBuffer buffer = null;
        FileChannel wpChannel = null;

        try {
            buffer = mapManager.getIOBufferPool().take();
            wpChannel = FileChannel.open(xWpFile.toPath(), StandardOpenOption.READ);
            wpChannel.lock(0, Long.MAX_VALUE, true);
            try {
                long wpSize = wpChannel.size();
                buffer = MapUtils.readFileToBuffer(wpChannel, buffer, wpSize);
                buffer.flip();
                wpChannel.close();
            } catch (IOException ex) {
                ShadowMap.getLogger().warn("Couldn't read file for import: " + xWpFile, ex);
                return false;
            }

            List<String> lines = IOUtils.readLines(new ByteBufferInputStream(buffer), StandardCharsets.UTF_8);
            Set<UUID> usedIds = new HashSet<>();
            for (String line : lines) {
                if (line.startsWith("#")) {
                    continue;
                }
                UUID id;
                while (!usedIds.add(id = wpManager.getUniqueID())) {}
                String[] split = line.split(":");
                if (split[0].equals("sets")) {
                    for (int i = 1; i < split.length; i++) {
                        String set = split[i];
                        if (set.equals("gui.xaero_default") || set.equals("xaero_default")) {
                            continue;
                        }
                        WaypointGroup group = new WaypointGroup(wpManager, id);
                        group.setName(set);
                        groupMap.put(set, group);
                        // Generate new ID after every group
                        while (!usedIds.add(id = wpManager.getUniqueID())) {}
                    }
                } else if (split[0].equals("waypoint")) {
                    String name = split[1];
                    String initials = split[2];
                    if (split[4].equals("~")) {
                        split[4] = "64";
                    }
                    Vector3d pos = new Vector3d(
                            Double.parseDouble(split[3]),
                            Double.parseDouble(split[4]),
                            Double.parseDouble(split[5])
                    );
                    int colorIndex = Integer.parseInt(split[6]);
                    boolean disabled = Boolean.parseBoolean(split[7]);
                    // TODO how many of these are guaranteed to exist within the past few years?
                    int type = Integer.parseInt(split[8]); // 0=user; 1=death; 2=olddeath
                    if (type == 1 || type == 2) {
                        deathPointSet.add(id);
                    }
                    String set = split[9];
                    boolean rotate = Boolean.parseBoolean(split[10]);
                    float yaw = Float.parseFloat(split[11]);
                    int visibleType;
                    if (split[12].matches("\\d+")) {
                        visibleType = Integer.parseInt(split[12]); // local/global
                    } else {
                        visibleType = Boolean.parseBoolean(split[12]) ? 0 : 1;
                    }
                    Waypoint waypoint = new Waypoint(wpManager, id);
                    waypoint.setName(name);
                    waypoint.setShortName(initials);
                    waypoint.setPos(pos);
                    Integer colorValue = Formatting.byColorIndex(colorIndex).getColorValue();
                    if (colorValue != null) {
                        waypoint.setColor(colorValue);
                    } else {
                        waypoint.setColor(MapUtils.hsvToRgb((float) Math.random(), 1.0F, 1.0F));
                    }
                    waypoint.setVisible(!disabled);
                    waypoint.setDir(0, yaw);
                    pointList.add(waypoint);
                    groupNameList.add(set.equals("gui.xaero_default") || set.equals("xaero_default") ? null : set);
                }
            }
        } catch (IOException ex) {
            ShadowMap.getLogger().error("Couldn't load waypoints for import: " + xWpFile, ex);
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (buffer != null) {
                mapManager.getIOBufferPool().release(buffer);
            }
            if (wpChannel != null) {
                try {
                    wpChannel.close();
                } catch (IOException ex) {
                    ShadowMap.getLogger().error("Couldn't close channel for import: " + xWpFile, ex);
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean importRegionsFromX(File xZipFile, MapManagerImpl mapManager, BlocksRegion blocksRegion) {
        ByteBuffer[] buffers = new ByteBuffer[2];
        FileChannel zipChannel = null;

        try {
            mapManager.getIOBufferPool().bulkTake(buffers);
            zipChannel = FileChannel.open(xZipFile.toPath(), StandardOpenOption.READ);
            for (int i = 0; i < 5; i++) {
                try {
                    zipChannel.lock(0, Long.MAX_VALUE, true);
                    break;
                } catch (OverlappingFileLockException lockException) {
                    if (i == 4) {
                        throw lockException;
                    } else {
                        Thread.sleep(100 * (i + 1));
                    }
                }
            }
            try {
                long zipSize = zipChannel.size();
                buffers[0] = MapUtils.readFileToBuffer(zipChannel, buffers[0], zipSize);
                buffers[0].flip();
                zipChannel.close();
            } catch (IOException ex) {
                ShadowMap.getLogger().warn("Couldn't read file for import: " + xZipFile, ex);
                return false;
            }

            {
                ZipInputStream xZipIn = new ZipInputStream(new ByteBufferInputStream(buffers[0]));
                ByteBufferOutputStream decompressedBuffer = new ByteBufferOutputStream(buffers[1]);
                ZipEntry xentry;
                while ((xentry = xZipIn.getNextEntry()) != null && !xentry.getName().startsWith("region")) {}
                if (xentry == null) {
                    ShadowMap.getLogger().warn("Couldn't find region in " + xZipFile);
                    return false;
                }
                xZipIn.transferTo(decompressedBuffer);
                buffers[1] = decompressedBuffer.getBuffer();
            }
            ByteBuffer buffer = buffers[1];
            buffer.flip();

            ByteBufferInputStream bufferedIn = new ByteBufferInputStream(buffer);
            DataInputStream dataIn = new DataInputStream(bufferedIn);

            if (dataIn.read() != 0xFF) {
                ShadowMap.getLogger().warn("Region file header was not 0xFF in " + xZipFile);
                return false;
            }
            int version = dataIn.readInt();

            if (version > 0x0006_0007) {
                ShadowMap.getLogger().warn("Unsupported Xaero map version 0x" + Integer.toHexString(version) + ", import will likely fail: " + xZipFile);
            }

            ImportContext context = new ImportContext(
                    blocksRegion, bufferedIn, dataIn,
                    version >>> 16,
                    version & 0xFFFF,
                    blocksRegion.getRegionContainer().getWorld().getBlockRegistry(),
                    blocksRegion.getRegionContainer().getWorld().getBiomeRegistry(),
                    new ArrayList<>(), new ArrayList<>());

            while (true) {
                int tileZ = dataIn.read();
                if (tileZ == -1) {
                    // End of region stream.
                    break;
                }
                int tileX = tileZ >> 4 & 0x0F;
                tileZ &= 0x0F;
                readTile(context, tileX, tileZ);
            }

            blocksRegion.setLastModified(1);
        } catch (IOException ex) {
            ShadowMap.getLogger().error("Couldn't load region for import: " + xZipFile, ex);
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            mapManager.getIOBufferPool().bulkRelease(buffers);
            if (zipChannel != null) {
                try {
                    zipChannel.close();
                } catch (IOException ex) {
                    ShadowMap.getLogger().error("Couldn't close channel for import: " + xZipFile, ex);
                    return false;
                }
            }
        }

        return true;
    }

    private static void readTile(ImportContext context, int tileX, int tileZ) throws IOException {
        ByteBufferInputStream bufferedIn = context.bufferedIn;
        DataInputStream dataIn = context.dataIn;
        for (int subChunkX = 0; subChunkX < 4; subChunkX++) {
            for (int subChunkZ = 0; subChunkZ < 4; subChunkZ++) {
                int chunkX = tileX << 2 | subChunkX;
                int chunkZ = tileZ << 2 | subChunkZ;

                bufferedIn.mark(4);
                if (dataIn.readInt() == -1) {
                    // Chunk not present
                    continue;
                }
                bufferedIn.reset();

                readChunk(context, chunkX, chunkZ);
            }
        }
    }

    private static void readChunk(ImportContext context, int chunkX, int chunkZ) throws IOException {
        DataInputStream dataIn = context.dataIn;
        int versionMajor = context.versionMajor;
        int versionMinor = context.versionMinor;

//        ShadowMap.getLogger().info("Chunk " + chunkX + " " + chunkZ);
        BlocksChunk chunk = context.blocksRegion.getChunk(chunkX, chunkZ, true);
        List<BlockState> blockStack = new ArrayList<>();
        IntList lightStack = new IntArrayList();

        for (int blockX = 0; blockX < 16; blockX++) {
            for (int blockZ = 0; blockZ < 16; blockZ++) {
                int flags = dataIn.readInt();
                blockStack.clear();

                // Determine block state
                BlockState state = readBlockState(context, flags);

                // Determine height and light
                int height = ((flags & HEIGHT1_MASK) >>> HEIGHT1_SHIFT | (flags & HEIGHT2_MASK) >>> HEIGHT2_SHIFT) << 20 >> 20;
                if ((flags & HAS_REDUNDANT_HEIGHT) != 0) {
                    dataIn.read();
                }
                if ((flags & HAS_MORE_REDUNDANT_HEIGHT ) != 0) {
                    dataIn.read();
                }
                int light = (flags & LIGHT_MASK) >>> LIGHT_SHIFT;

                if (!state.isAir()) {
                    blockStack.add(state);
                    lightStack.add(light);
                }

                // Load layers (transparent/liquid?)
                if ((flags & HAS_LAYERS) != 0) {
                    int layerCount = dataIn.read();
                    for (int i = 0; i < layerCount; i++) {
                        int layerFlags = dataIn.readInt();
                        // Determine block state
                        BlockState layerState =  readLayerBlockState(context, layerFlags);

                        // Dunno
                        if (versionMinor < 1 && (layerFlags & HAS_LAYERS) != 0) {
                            dataIn.readInt();
                        }
                        if ((layerFlags & HAS_EXTRA_INT) != 0) {
                            dataIn.readInt();
                        }

                        int layerLight = (layerFlags & LAYER_LIGHT_MASK) >>> LAYER_LIGHT_SHIFT;

                        if (!layerState.isAir()) {
                            blockStack.add(layerState);
                            lightStack.add(layerLight);
                        }
                    }
                }

                // Determine biome, if present.
                Biome biome = readBiome(context, flags);

                if (context.versionMinor == 2 && (flags & HAS_EXTRA_BYTE) != 0) {
                    dataIn.read();
                }

                setBlockStack(chunk, blockX, blockZ, height, blockStack, lightStack);
                chunk.setBiome(blockX, blockZ, biome);
            }
        }
        if (versionMinor >= 4) {
            dataIn.read(); // Dunno.
        }
        if (versionMinor >= 6) {
            dataIn.readInt(); // Also dunno. Something about cave mode.
            if (versionMinor >= 7) {
                dataIn.read(); // More dunno cave stuff.
            }
        }
        chunk.setLastModified(1);
    }

    private static void setBlockStack(BlocksChunk chunk, int blockX, int blockZ, int height, List<BlockState> blockStack, IntList lightStack) {
        BlockState solid = null;
        BlockState liquid = null;
        BlockState transparent = null;
        int solidIndex = 0;
        int liquidIndex = 0;
        int transparentIndex = 0;

        for (int i = 0; i < blockStack.size(); i++) {
            BlockState newBlock = blockStack.get(i);
            if (newBlock.isAir()) {
                continue;
            }
            FluidState newFluid = newBlock.getFluidState();
            MapBlockStateMutable mapState = (MapBlockStateMutable) newBlock;
            if (!mapState.shadowMap$isOpacitySet()) {
                MapUtils.updateOpacity(newBlock);
            }
            if (solid == null && mapState.shadowMap$isOpaque()) {
                solid = newBlock;
                solidIndex = i;
                liquid = null;
                transparent = null;
            } else if (!mapState.shadowMap$isOpaque() && (newFluid.isEmpty() || newBlock.getBlock() != newFluid.getBlockState().getBlock())) {
                transparent = newBlock;
                transparentIndex = i;
            }
            if (!newFluid.isEmpty()) {
                liquid = newBlock;
                liquidIndex = i;
            }
        }
        // Since block storage is palettized, nulls must be explicitly set to be added to the palette.
        if (solid != null) {
            chunk.setBlock(BlocksChunk.BlockType.OPAQUE, blockX, blockZ, solid, height, lightStack.getInt(solidIndex));
        } else {
            chunk.setBlock(BlocksChunk.BlockType.OPAQUE, blockX, blockZ, null, BlocksChunk.HEIGHT_OFFSET, 0);
        }
        if (liquid != null) {
            chunk.setBlock(BlocksChunk.BlockType.LIQUID, blockX, blockZ, liquid, height + liquidIndex, lightStack.getInt(liquidIndex));
        } else {
            chunk.setBlock(BlocksChunk.BlockType.LIQUID, blockX, blockZ, null, BlocksChunk.HEIGHT_OFFSET, 0);
        }
        if (transparent != null) {
            chunk.setBlock(BlocksChunk.BlockType.TRANSPARENT, blockX, blockZ, transparent, height + transparentIndex, lightStack.getInt(transparentIndex));
        } else {
            chunk.setBlock(BlocksChunk.BlockType.TRANSPARENT, blockX, blockZ, null, BlocksChunk.HEIGHT_OFFSET, 0);
        }
    }

    private static BlockState readBlockState(ImportContext context, int flags) throws IOException {
        if ((flags & IS_FUCKING_GRASS_OR_WATER) == 0) {
            return Blocks.GRASS_BLOCK.getDefaultState();
        }
        if (context.versionMajor == 0) {
            // TODO convert id to block
            int stateId = context.dataIn.readInt();
            return Blocks.STONE.getDefaultState();
        }
        if ((flags & PALETTE_BLOCK) != 0) {
            BlockState state;
            NbtCompound blockStateNbt = NbtIo.readCompound(context.dataIn);
            if (context.versionMajor < 6) {
                // TODO run datafixer before parse
            }
            state = MapUtils.blockStateFromNbt(context.blockRegistry, blockStateNbt);
            context.blockPalette.add(state);
            return state;
        }
        return context.blockPalette.get(context.dataIn.readInt());
    }

    private static BlockState readLayerBlockState(ImportContext context, int layerFlags) throws IOException {
        if ((layerFlags & IS_FUCKING_GRASS_OR_WATER) == 0) {
            return Blocks.WATER.getDefaultState();
        }
        if (context.versionMajor == 0) {
            // TODO convert id to block
            int stateId = context.dataIn.readInt();
            return Blocks.WATER.getDefaultState();
        }
        if ((layerFlags & LAYER_PALETTE_BLOCK) != 0) {
            BlockState layerState;
            NbtCompound blockStateNbt = NbtIo.readCompound(context.dataIn);
            if (context.versionMajor < 6) {
                // TODO run datafixer before parse
            }
            layerState = MapUtils.blockStateFromNbt(context.blockRegistry, blockStateNbt);
            context.blockPalette.add(layerState);
            return layerState;
        }
        return context.blockPalette.get(context.dataIn.readInt());
    }

    private static Biome readBiome(ImportContext context, int flags) throws IOException {
        if ((flags & HAS_BIOME) == 0) {
            return null;
        }
        if ((flags & PALETTE_BIOME) == 0) {
            return context.biomePalette.get(context.dataIn.readInt());
        }
        if ((flags & IS_OLD_BIOME) != 0) {
            int biomeId = context.dataIn.readInt(); // TODO convert id to biome
            return context.biomeRegistry.getValue(BiomeKeys.PLAINS.getValue());
        }
        String biomeString = context.dataIn.readUTF();
        Biome biome = context.biomeRegistry
                .getValueOrEmpty(new Identifier(biomeString))
                .orElse(context.biomeRegistry.getValue(BiomeKeys.PLAINS.getValue()));
        context.biomePalette.add(biome);
        return biome;
    }

    private static record ImportContext(
            BlocksRegion blocksRegion,
            ByteBufferInputStream bufferedIn,
            DataInputStream dataIn,
            int versionMajor,
            int versionMinor,
            Registry<Block> blockRegistry,
            RegistryWrapper<Biome> biomeRegistry,
            List<BlockState> blockPalette,
            List<Biome> biomePalette
    ) {}
}
