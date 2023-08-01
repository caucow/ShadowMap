package com.caucraft.shadowmap.api.map;

import com.caucraft.shadowmap.api.util.MergeResult;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtLong;

import java.io.IOException;

/**
 * A single 16*16 chunk in the map.<br>
 * <br>
 * <b>Implementation Note:</b><br>
 * An instance of this will exist for every chunk loaded in the map, so this
 * should be built to minimize memory footprint as much as possible with
 * processor use as a secondary concern. Context such as the chunk's position or
 * the region it is in are provided as arguments to the update methods, and any
 * new methods should follow the same pattern.<br>
 * <br>
 * <b>Thread Safety Note:</b><br>
 * ShadowMap is multithreaded to improve performance with minimal impact to
 * gameplay. It can be assumed that, at a minimum, rendering, disk IO, and chunk
 * updates are done on separate threads with region locking handled by
 * ShadowMap. As such, modifications to chunk and region data should only be
 * done by scheduling such a modification with the Map API, and reads should
 * avoid using non-thread-safe iterators or methods and data structures that
 * lack consistency when being written to and read from at the same time.
 * TODO "scheduling such a modification with the Map API" needs to be added.
 * @param <NbtContext> The type of the context provided by the region's NBT
 * save and load methods. Useful if there is a structure better saved at the
 * region level that includes data from individual chunks, such as a block or
 * biome palette. If none is needed, use {@link Void} and provide {@code null}.
 */
public abstract class MapChunk<NbtContext> implements ChunkUpdateConsumer {

    private long lastModified;

    public int estimateMemoryUsage() {
        return 24;
    }

    /**
     * Merges the "other" instance of this class into this one.<br>
     * <br>
     * This is generally called when a map file on disk is modified by another
     * program (such as when running multiple game instances) and the map needs
     * to determine which parts of a region or chunk need to be overwritten
     * (either in memory or on disk) due to being outdated.
     * @param o the "other" instance of this class to merge into this one.
     * @return the result of the merge operation. This should be updated
     * throughout the merge process following the guidelines laid out in
     * {@link MergeResult}.
     */
    public abstract MergeResult mergeFrom(MapChunk<NbtContext> o);

    /**
     * Sets the last modified time for this chunk. This should be called by the
     * chunk's update methods with the update's time argument whenever the
     * update causes the data in chis chunk to change.
     * @param modifiedMs the new modified time, in milliseconds.
     */
    public void setLastModified(long modifiedMs) {
        this.lastModified = modifiedMs;
    }

    /**
     * @return the time, in milliseconds, this chunk was last modified.
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Loads this object's data from an NBT compound.
     * @param root the root compound for this object.
     * @param context metadata for loading the object.
     * @throws IOException if the object cannot be loaded from the NBT provided.
     */
    public void loadFromNbt(NbtCompound root, NbtContext context) throws IOException {
        if (root.contains("lastModified")) {
            lastModified = root.getLong("lastModified");
        } else {
            lastModified = root.getLong("modified");
        }
    }

    /**
     * Saves this object's data to an NBT compound.
     * @param context metadata for saving the object.
     * @return an NbtCompound representing this object.
     */
    public NbtCompound saveToNbt(NbtContext context) {
        NbtCompound root = new NbtCompound();
        root.put("modified", NbtLong.of(lastModified));
        return root;
    }
}
