package com.caucraft.shadowmap.api.storage;

import com.caucraft.shadowmap.api.MapApi;
import com.caucraft.shadowmap.api.map.MapRegion;
import com.caucraft.shadowmap.api.map.RegionContainer;

/**
 * A key used to access third party metadata regions. A StorageKeyImpl is obtained
 * by registering a {@link StorageAdapter} with the {@link MapApi}.
 * @param <RegionType> ImportType of {@link MapRegion} that will be accessible using
 * this key. This will match the provided {@link StorageAdapter}.
 */
public interface StorageKey<RegionType> {
    /**
     * @deprecated for internal use
     */
    @Deprecated
    int keyId();

    /**
     * @deprecated for internal use
     */
    @Deprecated
    RegionType createStorage(RegionContainer regionContainer);
}
