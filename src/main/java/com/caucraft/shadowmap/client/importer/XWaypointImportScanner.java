package com.caucraft.shadowmap.client.importer;

import com.caucraft.shadowmap.api.util.ServerKey;
import net.minecraft.SharedConstants;
import net.minecraft.util.Identifier;
import net.minecraft.world.dimension.DimensionTypes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class XWaypointImportScanner extends ImportScanner {
    public XWaypointImportScanner() {
        super();
    }

    public XWaypointImportScanner(Path scanDir) {
        super(scanDir);
    }

    @Override
    public List<ImportSupplier> scanForImports() throws IOException {
        List<ImportSupplier> list = new ArrayList<>();
        Path scanDir = this.scanDir;
        if (scanDir == null) {
            scanDir = Paths.get("./XaeroWaypoints");
        }
        if (!Files.isDirectory(scanDir)) {
            return new ArrayList<>();
        }
        Iterator<Path> serverIterator = Files.list(scanDir)
                .filter(Files::isDirectory)
                .iterator();
        ServerFinder serverFinder = new ServerFinder(false);
        while (serverIterator.hasNext()) {
            Path serverPath = serverIterator.next();
            String server = serverPath.getFileName().toString();
            Iterator<Path> dimensionIterator = Files.list(serverPath)
                    .filter(Files::isDirectory)
                    .iterator();
            while (dimensionIterator.hasNext()) {
                Path dimensionPath = dimensionIterator.next();
                String dimension = dimensionPath.getFileName().toString();
                Iterator<Path> worldIterator = Files.list(dimensionPath)
                        .filter((path) -> !Files.isDirectory(path))
                        .iterator();
                while (worldIterator.hasNext()) {
                    Path worldPath = worldIterator.next();
                    String world = worldPath.getFileName().toString();
                    ImportSupplier importer = getImporter(
                            serverFinder,
                            server.toLowerCase(Locale.ROOT),
                            dimension.toLowerCase(Locale.ROOT),
                            world,
                            worldPath);
                    if (importer != null) {
                        list.add(importer);
                    }
                }
            }
        }
        return list;
    }

    private ImportSupplier getImporter(ServerFinder serverFinder, String server, String dimension, String world, Path worldPath) {
        ServerKey serverKey;
        boolean usesKnownServer;
        if (server.startsWith("multiplayer_")) {
            server = server.substring("multiplayer_".length());
            serverKey = serverFinder.getBestMatch(server, -1);
            usesKnownServer = serverKey != null;;
            if (!usesKnownServer) {
                serverKey = ServerKey.newKey(ServerKey.ServerType.MULTIPLAYER, server, SharedConstants.DEFAULT_PORT);
            }
        } else {
            // TODO add WorldFinder to complement ServerFinder
            // TODO add RealmFinder to complement ServerFinder and add proper support for realms connections
            server = server.replace("%us%", "_");
            serverKey = ServerKey.newKey(ServerKey.ServerType.SINGLEPLAYER, server, -1);
            usesKnownServer = false;
        }

        String toWorld;
        String toDimension;
        boolean multiworld = !world.startsWith("mw$default");

        dimension = dimension.replace('$', ':');
        if (dimension.startsWith("dim%")) {
            dimension = dimension.substring("dim%".length());
            if (dimension.equals("0")) {
                toDimension = DimensionTypes.OVERWORLD.getValue().getPath();
                toWorld = toDimension;
            } else if (dimension.equals("-1")) {
                toDimension = DimensionTypes.THE_NETHER.getValue().getPath();
                toWorld = toDimension;
            } else if (dimension.equals("1")) {
                toDimension = DimensionTypes.THE_END.getValue().getPath();
                toWorld = toDimension;
            } else if (dimension.startsWith(Identifier.DEFAULT_NAMESPACE + ":")) {
                toWorld = dimension.substring(Identifier.DEFAULT_NAMESPACE.length() + 1);
                toDimension = DimensionTypes.OVERWORLD.getValue().getPath();
            } else {
                toWorld = dimension;
                toDimension = DimensionTypes.OVERWORLD.getValue().getPath();
            }
        } else {
            toWorld = dimension;
            toDimension = DimensionTypes.OVERWORLD.getValue().getPath();
        }

        return new ImportSupplier(worldPath, ImportType.XAERO_WP, usesKnownServer, serverKey.type(), serverKey.nameOrIp(), serverKey.port(), toWorld, toDimension, multiworld ? "XaeroMap Multi-World Waypoint Data" : "XaeroMap Waypoint Data");
    }
}
