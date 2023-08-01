package com.caucraft.shadowmap.client.importer;

import com.caucraft.shadowmap.api.util.ServerKey;
import com.caucraft.shadowmap.api.util.WorldKey;

import java.nio.file.Path;
import java.util.UUID;

public class ImportSupplier {
    private Path importPath;
    private ImportType importType;
    private boolean usesKnownServer;
    private ServerKey.ServerType serverType;
    private String serverName;
    private int serverPort;
    private String worldName;
    private String dimensionName;
    private String description;
    private boolean usesDefaultDatapacks;
    private boolean manuallyAdded;

    public ImportSupplier(Path importPath, ImportType importType, boolean usesKnownServer, ServerKey.ServerType serverType, String serverName, int serverPort, String worldName, String dimensionName, String description) {
        this.importPath = importPath;
        this.importType = importType;
        this.usesKnownServer = usesKnownServer;
        this.serverType = serverType;
        this.serverName = serverName;
        this.serverPort = serverPort;
        this.worldName = worldName;
        this.dimensionName = dimensionName;
        this.description = description;
    }

    public ImportTask<?> getImportTask() {
        ServerKey serverKey = ServerKey.newKey(serverType, serverName, serverType == ServerKey.ServerType.MULTIPLAYER ? serverPort : -1);
        WorldKey worldKey = WorldKey.newKey(serverKey, worldName, dimensionName);
        ImportTask<?> task = importType.createTask(UUID.randomUUID(), worldKey, importPath.toFile());
        task.withDefaultDatapacks();
        return task;
    }

    public boolean isUsesKnownServer() {
        return usesKnownServer;
    }

    public void setUsesKnownServer(boolean usesKnownServer) {
        this.usesKnownServer = usesKnownServer;
    }

    public ServerKey.ServerType getServerType() {
        return serverType;
    }

    public void setServerType(ServerKey.ServerType serverType) {
        this.serverType = serverType;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public String getDimensionName() {
        return dimensionName;
    }

    public void setDimensionName(String dimensionName) {
        this.dimensionName = dimensionName;
    }

    public Path getImportPath() {
        return importPath;
    }

    public ImportType getImportType() {
        return importType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isUsesDefaultDatapacks() {
        return usesDefaultDatapacks;
    }

    public void setUsesDefaultDatapacks(boolean usesDefaultDatapacks) {
        this.usesDefaultDatapacks = usesDefaultDatapacks;
    }

    public boolean isManuallyAdded() {
        return manuallyAdded;
    }

    public void setManuallyAdded(boolean manuallyAdded) {
        this.manuallyAdded = manuallyAdded;
    }
}
