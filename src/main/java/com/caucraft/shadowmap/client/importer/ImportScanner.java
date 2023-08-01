package com.caucraft.shadowmap.client.importer;

import com.caucraft.shadowmap.client.ShadowMap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class ImportScanner {
    protected final Path scanDir;

    public ImportScanner() {
        this.scanDir = null;
    }

    public ImportScanner(Path scanDir) {
        this.scanDir = scanDir;
    }

    protected abstract List<ImportSupplier> scanForImports() throws IOException;

    public CompletableFuture<List<ImportSupplier>> getImportSuppliers() {
        return ShadowMap.getInstance().getMapManager().executeNonLockingIOTask(this::scanForImports);
    }
}
