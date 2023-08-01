package com.caucraft.shadowmap.api.map;

import com.caucraft.shadowmap.api.util.ChunkCache;
import com.caucraft.shadowmap.api.util.MergeResult;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.io.IOException;

/**
 * A region in the map, normally encompassing a 512*512 block area just as
 * Minecraft's world regions do.<br>
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
 * @param <ChunkType> The type of chunks contained by this region.
 * @param <ChunkNbtContext> The type of the context provided by the region's NBT
 * save and load methods. Useful if there is a structure better saved at the
 * region level that includes data from individual chunks, such as a block or
 * biome palette. If none is needed, use {@link Void}.
 */
public abstract class MapRegion<ChunkType extends MapChunk<ChunkNbtContext>, ChunkNbtContext>
        implements ChunkUpdateConsumer {

    public static final int NBT_VERSION = 3; // TODO increase after save format change

    private int loadedNbtVersion;

    protected transient final RegionContainer regionContainer;
    protected final ChunkType[] chunkArray;
    private transient long lastSaved;
    private long lastModified;

    protected MapRegion(RegionContainer regionContainer) {
        this.regionContainer = regionContainer;
        this.chunkArray = supplyChunkArray(1024);
        this.loadedNbtVersion = NBT_VERSION;
    }

    /**
     * Called in the constructor to create an array of chunks. This should ONLY
     * create the new array, its contents should remain null until initialized
     * by a chunk update.
     * @return the new chunk array, or null if this region does not need chunk-
     * specific data.
     */
    protected abstract ChunkType[] supplyChunkArray(int size);

    /**
     * Called during a chunk update to create a new chunk if one did not already
     * exist.
     * @return the new chunk.
     */
    protected abstract ChunkType supplyChunk();

    /**
     * Called when the region is being saved-to or loaded-from NBT to supply a
     * context object that will be passed to each chunk as it is saved or
     * loaded.
     * @return the new context object, or null if none is needed/the context
     * type is {@link Void}.
     */
    protected abstract ChunkNbtContext supplyNbtContext();

    public int estimateMemoryUsage() {
        int usage = 68;
        ChunkType[] chunks = this.chunkArray;
        for (int i = 0; i < chunks.length; i++) {
            ChunkType chunk = chunks[i];
            if (chunk == null) {
                continue;
            }
            usage += 16 + chunk.estimateMemoryUsage();
        }
        return usage;
    }

    public RegionContainer getRegionContainer() {
        return regionContainer;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long modifiedMs) {
        this.lastModified = modifiedMs;
    }

    public long getLastSaved() {
        return lastSaved;
    }

    public void setLastSaved(long savedMs) {
        this.lastSaved = savedMs;
    }

    public boolean isModified() {
        return lastModified > lastSaved;
    }

    public final ChunkType getChunk(int chunkX, int chunkZ, boolean create) {
        if (chunkArray == null) {
            return null;
        }
        int index = getChunkIndex(chunkX, chunkZ);
        ChunkType chunk = chunkArray[index];
        if (chunk == null && create) {
            return chunkArray[index] = supplyChunk();
        }
        return chunk;
    }

    protected final int getChunkIndex(int chunkX, int chunkZ) {
        return (chunkZ & 0x1F) << 5 | (chunkX & 0x1F);
    }

    public boolean updateChunk(World world, Chunk chunk, ChunkCache adjChunkCache, CeilingType ceilingType,
            long curTimeMs) {
        return false;
    }

    public boolean updateBlock(World world, Chunk chunk, ChunkCache adjChunkCache, CeilingType ceilingType,
            BlockPos pos, BlockState state, long curTimeMs) {
        return false;
    }

    public boolean updateSurroundedChunk(World world, Chunk chunk, ChunkCache adjChunkCache, CeilingType ceilingType,
            long curTimeMs) {
        return false;
    }

    /**
     * Merges the "other" instance of this class into this one.<br>
     * <br>
     * This is generally called when a map file on disk is modified by another
     * program (such as when running multiple game instances) and the map needs
     * to determine which parts of a region or chunk need to be overwritten
     * (either in memory or on disk) due to being outdated.
     * @param other the "other" instance of this class to merge into this one.
     * @return the result of the merge operation. This should be updated
     * throughout the merge process following the guidelines laid out in
     * {@link MergeResult}.
     */
    public MergeResult mergeFrom(MapRegion<ChunkType, ChunkNbtContext> other) {
        MergeResult result = MergeResult.getResult();
        if (this == other) {
            return result;
        }
        if (chunkArray == null) {
            return result;
        }
        long newLastModified = 0;
        for (int i = 0; i < chunkArray.length; i++) {
            ChunkType myChunk = chunkArray[i];
            ChunkType otherChunk = other.chunkArray[i];
            if (otherChunk != null && myChunk != null) {
                MergeResult chunkResult = myChunk.mergeFrom(otherChunk);
                if (chunkResult.isUsedBoth()) {
                    myChunk.setLastModified(Math.max(myChunk.getLastModified(), other.getLastModified()));
                } else if (chunkResult.isUsedOther()) {
                    myChunk.setLastModified(other.getLastModified());
                }
                result = result.includeResult(chunkResult);
            } else if (otherChunk != null) {
                chunkArray[i] = myChunk = otherChunk;
                result = result.usedOther();
            } else if (myChunk != null) {
                result = result.usedThis();
            }
            if (myChunk != null) {
                newLastModified = Math.max(newLastModified, myChunk.getLastModified());
            }
        }
        if (!result.isUsedThis()) {
            setLastSaved(other.getLastSaved());
        } else if (result.isUsedOther()) {
            setLastSaved(Math.min(getLastSaved(), other.getLastSaved()));
            if (other.loadedNbtVersion < NBT_VERSION) {
                regionContainer.setFlag(RegionFlags.FORCE_SAVE);
            }
        }
        setLastModified(newLastModified);
        return result;
    }

    /**
     * Loads this object's data from an NBT compound.
     * @param root the root compound for this object.
     * @throws IOException if the object cannot be loaded from the NBT provided.
     */
    public void loadFromNbt(NbtCompound root) throws IOException {
        ChunkNbtContext chunkContext = supplyNbtContext();
        if (chunkContext != null && root.contains("meta", NbtElement.COMPOUND_TYPE)) {
            loadNbtContext(root.getCompound("meta"), chunkContext);
        }

        long modified = 0;
        if (chunkArray != null && root.contains("chunks", NbtElement.COMPOUND_TYPE)) {
            NbtCompound chunks = root.getCompound("chunks");
            for (int z = 0; z < 32; z++) {
                for (int x = 0; x < 32; x++) {
                    String key = x + "," + z;
                    if (!chunks.contains(key, NbtElement.COMPOUND_TYPE)) {
                        continue;
                    }
                    ChunkType chunk = getChunk(x, z, true);
                    if (chunk == null) {
                        continue;
                    }
                    chunk.loadFromNbt(chunks.getCompound(key), chunkContext);
                }
            }

            for (int i = 0; i < chunkArray.length; i++) {
                ChunkType chunk = chunkArray[i];
                if (chunk != null) {
                    modified = Math.max(modified, chunk.getLastModified());
                }
            }
        }

        setLastModified(modified);

        this.loadedNbtVersion = root.getInt("nbtVA");
    }

    /**
     * Saves this object's data to an NBT compound.
     * @return an NbtCompound representing this object.
     */
    public NbtCompound saveToNbt() {
        NbtCompound root = new NbtCompound();

        ChunkNbtContext chunkContext = supplyNbtContext();

        if (chunkArray != null) {
            NbtCompound chunks = new NbtCompound();
            for (int z = 0; z < 32; z++) {
                for (int x = 0; x < 32; x++) {
                    ChunkType chunk = getChunk(x, z, false);
                    if (chunk == null) {
                        continue;
                    }
                    NbtCompound chunkNbt = chunk.saveToNbt(chunkContext);
                    if (chunkNbt != null) {
                        chunks.put(x + "," + z, chunkNbt);
                    }
                }
            }
            root.put("chunks", chunks);
        }

        if (chunkContext != null) {
            NbtCompound metaNbt = saveNbtContext(chunkContext);
            if (metaNbt != null) {
                root.put("meta", metaNbt);
            }
        }

        root.put("nbtVA", NbtInt.of(loadedNbtVersion));

        return root;
    }

    protected abstract void loadNbtContext(NbtCompound contextRoot, ChunkNbtContext context) throws IOException;

    protected abstract NbtCompound saveNbtContext(ChunkNbtContext context);
}
