package com.caucraft.shadowmap.client.map;

import com.caucraft.shadowmap.client.util.MapUtils;
import com.caucraft.shadowmap.client.util.data.PaletteMap;
import com.caucraft.shadowmap.api.util.RegistryWrapper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.io.IOException;

public class BlocksNbtContext {
    private final RegistryWrapper<Biome> biomeRegistry;
    private final Registry<Block> blockRegistry;
    private final PaletteMap<BlockState> blockPalette;
    private final PaletteMap<Biome> biomePalette;

    public BlocksNbtContext(RegistryWrapper<Biome> biomeRegistry, Registry<Block> blockRegistry) {
        this.biomeRegistry = biomeRegistry;
        this.blockRegistry = blockRegistry;
        this.blockPalette = new PaletteMap<>();
        this.biomePalette = new PaletteMap<>();
    }

    public RegistryWrapper<Biome> getBiomeRegistry() {
        return biomeRegistry;
    }

    public Registry<Block> getBlockRegistry() {
        return blockRegistry;
    }

    public PaletteMap<BlockState> getBlockPalette() {
        return blockPalette;
    }

    public PaletteMap<Biome> getBiomePalette() {
        return biomePalette;
    }

    public void loadFromNbt(NbtCompound root) throws IOException {
        if (!root.contains("blocks", NbtElement.LIST_TYPE)) {
            throw new IOException("Block palette was missing from block data."); // TODO do I throw here?
        }
        if (!root.contains("biomes", NbtElement.LIST_TYPE)) {
            throw new IOException("Biome palette was missing from block data."); // TODO do I throw here?
        }
        blockPalette.loadNbt(
                root.getList("blocks", NbtElement.STRING_TYPE),
                (blockString) -> MapUtils.blockStateFromString(blockRegistry, blockString)
        );
        biomePalette.loadNbt(
                root.getList("biomes", NbtElement.STRING_TYPE),
                (biomeString) -> biomeRegistry
                        .getValueOrEmpty(new Identifier(biomeString))
                        .orElse(biomeRegistry.getValue(BiomeKeys.PLAINS.getValue()))
        );
    }

    public NbtCompound saveToNbt() {
        NbtCompound root = new NbtCompound();
        root.put("blocks", blockPalette.getNbt());
        root.put("biomes", biomePalette.getNbt());
        return root;
    }
}
