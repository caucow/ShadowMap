package com.caucraft.shadowmap.api.map;

import com.caucraft.shadowmap.api.storage.StorageKey;

public interface RegionContainer {

    MapWorld getWorld();
    int getRegionX();
    int getRegionZ();
    <RegionType> RegionType getStorage(StorageKey<RegionType> key);
    <RegionType> RegionType getOrCreateStorage(StorageKey<RegionType> key);

    /**
     * Sets a flag in the region.
     * @param flag the flag to set.
     * @return true if the flags changed, false otherwise.
     */
    default boolean setFlag(RegionFlags flag) {
        return setFlags(flag.flag);
    }

    /**
     * Clears a flag in the region.
     * @param flag the flag to clear.
     * @return true if the flags changed, false otherwise.
     */
    default boolean clearFlag(RegionFlags flag) {
        return clearFlags(flag.flag);
    }

    /**
     * Determines if a flag is set in the region.
     * @param flag the flag to test.
     * @return true if the flag is set, false otherwise.
     */
    default boolean isFlagSet(RegionFlags flag) {
        return isFlagsSet(flag.flag);
    }

    /**
     * Sets the flags in the region.
     * @param addedFlags the flags to set.
     * @return true if the flags changed, false otherwise.
     */
    boolean setFlags(int addedFlags);

    /**
     * Clears the flags in the region.
     * @param removedFlags the flags to clear.
     * @return true if the flags changed, false otherwise.
     */
    boolean clearFlags(int removedFlags);

    /**
     * Determines if all provided flags are set in the region.
     * @param flags the flags to check.
     * @return true if all the provided flags are set, false otherwise.
     */
    boolean isFlagsSet(int flags);

    /**
     * Get the current value of the provided flags.
     * @param flags the flags to check.
     * @return the value of the provided flags.
     */
    int getFlags(int flags);

    /**
     * @return the current flags integer.
     */
    int getFlags();
}
