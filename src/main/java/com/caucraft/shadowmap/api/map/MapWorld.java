package com.caucraft.shadowmap.api.map;

import com.caucraft.shadowmap.api.util.RegistryWrapper;
import com.caucraft.shadowmap.api.util.RenderArea;
import com.caucraft.shadowmap.api.util.WorldKey;
import net.minecraft.block.Block;
import net.minecraft.registry.Registry;
import net.minecraft.world.biome.Biome;

public interface MapWorld {

    MapManager getMapManager();
    WorldKey getWorldKey();
    Registry<Block> getBlockRegistry();
    RegistryWrapper<Biome> getBiomeRegistry();

    boolean isEmpty();
    RegionContainer getRegion(int regionX, int regionZ, boolean create, boolean load);

    RenderArea getRenderArea(LoadLevel loadLevel);

    enum LoadLevel {
        RENDER_DISTANCE_FORCED(RegionFlags.RENDER_DISTANCE_FORCED),
        FULL_MAP_ZOOM_IN(RegionFlags.FULLMAP_ZOOM_IN),
        FULL_MAP_ZOOM_OUT(RegionFlags.FULLMAP_ZOOM_OUT),
        MINIMAP_ZOOM(RegionFlags.MINIMAP_ZOOM);

        public static final int LOAD_FLAGS_MASK;

        static {
            int flags = 0;
            LoadLevel[] values = values();
            for (int i = 0; i < values.length; i++) {
                flags |= values[0].loadFlag.flag;
            }
            LOAD_FLAGS_MASK = flags;
        }

        public final RegionFlags loadFlag;

        LoadLevel(RegionFlags loadFlag) {
            this.loadFlag = loadFlag;
        }
    }

}
