package com.caucraft.shadowmap.client.importer;

import com.caucraft.shadowmap.api.util.WorldKey;
import com.caucraft.shadowmap.client.util.TriFunction;
import net.minecraft.nbt.NbtCompound;

import java.io.File;
import java.util.UUID;
import java.util.function.Supplier;

public enum ImportType {
    XAERO(XImportTask::new, XImportScanner::new),
    XAERO_WP(XWaypointImportTask::new, XWaypointImportScanner::new);

    private final TriFunction<UUID, WorldKey, File, ImportTask<?>> createTaskFunction;
    private final Supplier<ImportScanner> scannerSupplier;

    ImportType(TriFunction<UUID, WorldKey, File, ImportTask<?>> createTaskFunction, Supplier<ImportScanner> scannerSupplier) {
        this.createTaskFunction = createTaskFunction;
        this.scannerSupplier = scannerSupplier;
    }

    public ImportTask<?> createTask(UUID id, WorldKey worldKey, File sourceDir) {
        return createTaskFunction.apply(id, worldKey, sourceDir);
    }

    public ImportScanner getScanner() {
        return scannerSupplier.get();
    }

    public static ImportTask<?> fromNbt(UUID id, NbtCompound root) {
        ImportTask<?> task = ImportType.valueOf(root.getString("type"))
                .createTask(
                        id,
                        WorldKey.fromNbt(root.getCompound("world")),
                        new File(root.getString("dir"))
                );
        task.setPaused(true);
        return task;
    }
}
