package com.caucraft.shadowmap.client.importer;

import com.caucraft.shadowmap.api.util.WorldKey;
import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.map.MapManagerImpl;
import com.caucraft.shadowmap.client.map.MapWorldImpl;
import com.caucraft.shadowmap.client.util.data.DeletableLiveObject;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtString;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public abstract class ImportTask<T extends ImportTask.ImportOp> extends DeletableLiveObject {

    private final WorldKey worldKey;
    private boolean usingDefaultDatapacks;
    protected final File importFile;
    private int errorCount;
    private long finishTime;
    private boolean paused;
    protected transient MapWorldImpl world;
    protected transient Object forceLoader;
    protected transient Iterator<Path> iterator;

    public ImportTask(UUID id, WorldKey worldKey, File importFile) {
        super(id);
        this.worldKey = worldKey;
        this.importFile = importFile;
    }

    public void withDefaultDatapacks() {
        if (world != null) {
            return;
        }
        usingDefaultDatapacks = true;
    }

    public boolean isUsingDefaultDatapacks() {
        return usingDefaultDatapacks;
    }

    public WorldKey getWorldKey() {
        return worldKey;
    }

    public File getImportFile() {
        return importFile;
    }

    public float getProgress() {
        if (!isReady()) {
            if (!isInitializing()) {
                return 0.0F;
            }
            return 0.25F;
        }
        if (!isDone()) {
            return 0.5F;
        }
        return 1.0F;
    }

    public boolean isReady() {
        return iterator != null;
    }

    public boolean isDone() {
        boolean done = iterator != null && !iterator.hasNext();
        if (done && finishTime == 0) {
            finishTime = ShadowMap.getLastTickTimeS();
            forceLoader = null;
        }
        return done;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    protected abstract Stream<Path> getPathStream() throws IOException;

    public synchronized void init(MapWorldImpl world, Object forceLoader) throws IOException {
        if (world != null && world.getWorldKey().equals(worldKey)) {
            this.world = world;
            this.forceLoader = forceLoader;
        }
        if (isReady()) {
            return;
        }
        iterator = getPathStream().iterator();
    }

    public boolean isInitializing() {
        return this.world != null && !isReady();
    }

    public synchronized T nextImportOp() {
        if (isDone()) {
            return null;
        }
        while (iterator.hasNext()) {
            T nextOp = getImportOp(iterator.next());
            if (nextOp == null) {
                continue;
            }
            return nextOp;
        }
        return null;
    }

    protected abstract T getImportOp(Path path);
    public abstract ImportType getType();

    public void mergeProgress(ImportTask<?> other) {}

    public boolean isSameTask(ImportTask<?> other) {
        return worldKey.equals(other.worldKey) && importFile.equals(other.importFile) && getType() == other.getType();
    }

    public long getFinishTime() {
        return finishTime;
    }

    void addError() {
        errorCount++;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public NbtCompound toNbt() {
        NbtCompound root = super.toNbt();
        root.put("type", NbtString.of(getType().name()));
        root.put("world", worldKey.toNbt());
        root.put("dir", NbtString.of(importFile.toString()));
        if (finishTime != 0) {
            root.put("finish", NbtLong.of(finishTime));
        }
        if (usingDefaultDatapacks) {
            root.put("defaultData", NbtByte.of(usingDefaultDatapacks));
        }
        if (errorCount != 0) {
            root.put("errors", NbtInt.of(errorCount));
        }
        return root;
    }

    public void loadNbt(NbtCompound root) {
        super.loadNbt(root);
        if (root.contains("finish", NbtElement.LONG_TYPE)) {
            iterator = Collections.emptyIterator();
            finishTime = root.getLong("finish");
        }
        if (root.contains("defaultData", NbtElement.BYTE_TYPE)) {
            usingDefaultDatapacks = root.getBoolean("defaultData");
        }
        if (root.contains("errors", NbtElement.INT_TYPE)) {
            errorCount = root.getInt("errors");
        }
    }

    @Override
    public String toString() {
        return "ImportTask{"
                + "type=" + getType()
                + ", worldKey=" + worldKey
                + ", id=" + getId()
                + ", importFolder=" + importFile + '}';
    }

    public static abstract class ImportOp extends CompletableFuture<Void> {
        protected final File importFile;

        protected ImportOp(File importFile) {
            this.importFile = importFile;
        }

        public File getImportFile() {
            return importFile;
        }

        abstract void loadFile(MapManagerImpl mapManager) throws IOException;
        abstract CompletableFuture<?> scheduleMerge(MapManagerImpl mapManager);
    }
}
