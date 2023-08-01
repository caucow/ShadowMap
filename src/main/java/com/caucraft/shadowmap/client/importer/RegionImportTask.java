package com.caucraft.shadowmap.client.importer;

import com.caucraft.shadowmap.api.util.WorldKey;
import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.map.MapWorldImpl;
import com.caucraft.shadowmap.client.util.data.RegionBitSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class RegionImportTask<T extends RegionImportTask.RegionImportOp> extends ImportTask<T> {

    private final RegionBitSet progressBitSet;
    private transient final AtomicInteger totalCount;
    private transient final AtomicInteger importedCount;

    public RegionImportTask(UUID id, WorldKey worldKey, File importFile) {
        super(id, worldKey, importFile);
        this.progressBitSet = new RegionBitSet();
        this.totalCount = new AtomicInteger(-1);
        this.importedCount = new AtomicInteger(0);
    }

    @Override
    public float getProgress() {
        int cur = importedCount.get();
        int total = totalCount.get();
        if (total < 0) {
            return -1.0F;
        }
        return (float) cur / (float) total;
    }

    @Override
    public synchronized void init(MapWorldImpl world, Object forceLoader) throws IOException {
        if (isReady()) {
            return;
        }
        super.init(world, forceLoader);
        importedCount.set(0);
        totalCount.set((int) getPathStream().count());
    }

    @Override
    public synchronized T nextImportOp() {
        if (isDone()) {
            return null;
        }
        while (iterator.hasNext()) {
            T nextOp = getImportOp(iterator.next());
            if (nextOp == null) {
                continue;
            }
            if (progressBitSet.getRegion(nextOp.getRegionX(), nextOp.getRegionZ())) {
                importedCount.getAndIncrement();
                continue;
            }
            nextOp.whenComplete((val, ex) -> {
                importedCount.getAndIncrement();
                if (ex == null) {
                    progressBitSet.setRegion(nextOp.getRegionX(), nextOp.getRegionZ(), true);
                }
            });
            return nextOp;
        }
        return null;
    }

    @Override
    public void mergeProgress(ImportTask<?> other) {
        super.mergeProgress(other);
        if (other instanceof RegionImportTask<?> regionOther) {
            progressBitSet.merge(regionOther.progressBitSet);
        }
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound root = super.toNbt();
        if (!isDone()) {
            root.put("progress", progressBitSet.toNbt());
        }
        return root;
    }

    @Override
    public void loadNbt(NbtCompound root) {
        super.loadNbt(root);
        if (!root.contains("finish", NbtElement.LONG_TYPE) && root.contains("progress", NbtElement.COMPOUND_TYPE)) {
            try {
                progressBitSet.loadNbt(root.getCompound("progress"));
            } catch (IOException ex) {
                ShadowMap.getLogger().warn("Progress could not be loaded for import task and will be reset", ex);
            }
        }
    }

    public static abstract class RegionImportOp extends ImportOp {
        protected final int regionX, regionZ;

        protected RegionImportOp(File importFile, int regionX, int regionZ) {
            super(importFile);
            this.regionX = regionX;
            this.regionZ = regionZ;
        }

        public int getRegionX() {
            return regionX;
        }

        public int getRegionZ() {
            return regionZ;
        }
    }
}
