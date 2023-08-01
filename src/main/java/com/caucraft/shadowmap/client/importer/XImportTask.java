package com.caucraft.shadowmap.client.importer;

import com.caucraft.shadowmap.api.map.RegionFlags;
import com.caucraft.shadowmap.api.util.MergeResult;
import com.caucraft.shadowmap.api.util.WorldKey;
import com.caucraft.shadowmap.client.map.BlocksRegion;
import com.caucraft.shadowmap.client.map.MapManagerImpl;
import com.caucraft.shadowmap.client.map.RegionContainerImpl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class XImportTask extends RegionImportTask<XImportTask.XImportOp> {

    private static final Pattern ZIP_PATTERN = Pattern.compile("(?<x>-?\\d+)_(?<z>-?\\d+)\\.zip");

    public XImportTask(UUID id, WorldKey worldKey, File importFolder) {
        super(id, worldKey, importFolder);
    }

    @Override
    protected Stream<Path> getPathStream() throws IOException {
        return Files.list(importFile.toPath()).filter((path) -> ZIP_PATTERN.matcher(path.getFileName().toString()).matches());
    }

    @Override
    public XImportOp getImportOp(Path path) {
        File file = path.toFile();
        Matcher nameMatcher = ZIP_PATTERN.matcher(file.getName().toLowerCase(Locale.ROOT));
        if (!nameMatcher.matches()) {
            return null;
        }

        int regionX = Integer.parseInt(nameMatcher.group("x"));
        int regionZ = Integer.parseInt(nameMatcher.group("z"));
        return new XImportOp(file, regionX, regionZ);
    }

    @Override
    public ImportType getType() {
        return ImportType.XAERO;
    }

    class XImportOp extends RegionImportOp {
        private RegionContainerImpl regionContainer;
        private BlocksRegion xBlocks;

        public XImportOp(File importFile, int regionX, int regionZ) {
            super(importFile, regionX, regionZ);
            this.whenComplete((val, ex) -> {
                RegionContainerImpl rc = regionContainer;
                if (rc != null) {
                    rc.clearFlag(RegionFlags.IMPORTING);
                }
            });
        }

        @Override
        public void loadFile(MapManagerImpl mapManager) throws IOException {
            regionContainer = world.getRegion(regionX, regionZ, true, true);
            regionContainer.setFlag(RegionFlags.IMPORTING);
            xBlocks = new BlocksRegion(regionContainer);
            XImporter.importRegionsFromX(importFile, mapManager, xBlocks);
            xBlocks.setLastSaved(0);
        }

        @Override
        public CompletableFuture<?> scheduleMerge(MapManagerImpl mapManager) {
            return regionContainer.scheduleUpdate(() -> {
                BlocksRegion other = regionContainer.getOrUseBlocks(xBlocks);
                MergeResult mergeResult = other.mergeFrom(xBlocks);
                if (other == xBlocks || mergeResult.isUsedOther()) {
                    regionContainer.setFlag(RegionFlags.FORCE_SAVE);
                    world.scheduleRegionSave(regionContainer, null);
                    regionContainer.scheduleRerenderAll(true);
                }
            });
        }
    }
}
