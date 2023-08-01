package com.caucraft.shadowmap.client.util;

import com.caucraft.shadowmap.api.MapExtensionInitializer;
import net.fabricmc.loader.api.metadata.ModMetadata;

public final class ApiUser<T> {
    public final ModData mod;
    public final T user;

    public ApiUser(MapExtensionInitializer mapExt, ModMetadata modMeta, T user) {
        String modId = mapExt.modId();
        if (modId == null) {
            modId = modMeta.getId();
        }
        this.mod = new ModData(modMeta, modId);
        this.user = user;
    }

    public static class ModData {
        public final ModMetadata meta;
        public final String modId;

        ModData(ModMetadata meta, String modId) {
            this.meta = meta;
            this.modId = modId;
        }
    }
}
