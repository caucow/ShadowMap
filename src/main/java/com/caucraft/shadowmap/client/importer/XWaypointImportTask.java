package com.caucraft.shadowmap.client.importer;

import com.caucraft.shadowmap.api.util.WorldKey;
import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.map.MapManagerImpl;
import com.caucraft.shadowmap.client.util.MapUtils;
import com.caucraft.shadowmap.client.waypoint.Waypoint;
import com.caucraft.shadowmap.client.waypoint.WaypointConstants;
import com.caucraft.shadowmap.client.waypoint.WaypointGroup;
import com.caucraft.shadowmap.client.waypoint.WorldWaypointManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class XWaypointImportTask extends ImportTask<XWaypointImportTask.XWaypointImportOp> {

    private static final Pattern FILENAME_PATTERN = Pattern.compile("mw(?<w>.+)_(?<v>\\d+).txt");

    public XWaypointImportTask(UUID id, WorldKey worldKey, File importFolder) {
        super(id, worldKey, importFolder);
    }

    @Override
    protected Stream<Path> getPathStream() throws IOException {
        return Stream.of(importFile.toPath());
    }

    @Override
    protected XWaypointImportOp getImportOp(Path path) {
        return new XWaypointImportOp(path.toFile());
    }

    @Override
    public ImportType getType() {
        return ImportType.XAERO_WP;
    }

    class XWaypointImportOp extends ImportOp {
        private final Map<String, WaypointGroup> groupMap;
        private final List<Waypoint> pointList;
        private final List<String> groupNameList;
        private final Set<UUID> deathPointSet;
        private final String fileWorld;
        private final int fileVersion;

        public XWaypointImportOp(File importFile) {
            super(importFile);
            this.groupMap = new HashMap<>();
            this.pointList = new ArrayList<>();
            this.groupNameList = new ArrayList<>();
            this.deathPointSet = new HashSet<>();
            Matcher m = FILENAME_PATTERN.matcher(importFile.getName());
            if (!m.matches()) {
                fileWorld = null;
                fileVersion = 0;
            } else {
                fileWorld = m.group("w").toLowerCase(Locale.ROOT);
                int versionNum = 0;
                try {
                    versionNum = Integer.parseInt(m.group("v"));
                } catch (NumberFormatException ignored) {}
                fileVersion = versionNum;
            }
        }

        @Override
        void loadFile(MapManagerImpl mapManager) throws IOException {
            XImporter.importWaypointsFromX(importFile, mapManager, world.getWaypointManager(), groupMap, pointList, groupNameList, deathPointSet);
        }

        @Override
        CompletableFuture<?> scheduleMerge(MapManagerImpl mapManager) {
            WorldWaypointManager wpManager = world.getWaypointManager();
            return mapManager.executeGlobalModifyTask(() -> {
                UUID defaultGroupId = null;
                Optional<UUID> deathsGroupId = wpManager.getDeathsGroupId(ShadowMap.getInstance().getConfig().waypointConfig);
                // TODO handle death points
                if (!fileWorld.equals("$default")) {
                    WaypointGroup defaultGroup = new WaypointGroup(wpManager);
                    defaultGroup.setName(fileWorld);
                    defaultGroup.setColor(MapUtils.hsvToRgb((float) Math.random(), 1.0F, 1.0F));
                    defaultGroup.setAutoResize(false, false);
                    defaultGroup.setPos(0, 0, 0);
                    defaultGroup.setDrawCollapsed(WaypointGroup.DrawMode.NONE);
                    defaultGroup.setDrawExpanded(WaypointGroup.DrawMode.NONE);
                    defaultGroupId = defaultGroup.getId();
                    wpManager.addOrMoveWaypoint(defaultGroup, null);
                }

                boolean configureGroup = deathsGroupId.isEmpty() || wpManager.getWaypointGroup(deathsGroupId.get()).isEmpty();
                if (!deathPointSet.isEmpty() && deathsGroupId.isEmpty()) {
                    WaypointGroup deathsGroup = new WaypointGroup(wpManager, WaypointConstants.DEATHS_GROUP_ID);
                    wpManager.addOrMoveWaypoint(deathsGroup, null);
                    deathsGroupId = Optional.of(WaypointConstants.DEATHS_GROUP_ID);
                } else {
                    configureGroup = false;
                }

                for (WaypointGroup group : groupMap.values()) {
                    wpManager.addOrMoveWaypoint(group, defaultGroupId);
                }
                for (int i = 0; i < pointList.size(); i++) {
                    String group = groupNameList.get(i);
                    Waypoint waypoint = pointList.get(i);
                    UUID groupId = deathPointSet.contains(waypoint.getId()) ? deathsGroupId.get() : group == null ? defaultGroupId : groupMap.get(group).getId();
                    wpManager.addOrMoveWaypoint(waypoint, groupId);
                }

                if (configureGroup) {
                    WaypointGroup deathGroup = wpManager.getWaypointGroup(deathsGroupId.get()).get();
                    deathGroup.setName(ShadowMap.getInstance().getConfig().waypointConfig.defaultDeathGroupName.get());
                    deathGroup.setAutoResize(false, false);
                    deathGroup.setPos(0, 0, 0);
                    deathGroup.setDrawExpanded(WaypointGroup.DrawMode.NONE);
                    deathGroup.setDrawCollapsed(WaypointGroup.DrawMode.NONE);
                }

                world.scheduleWaypointSave();
                return null;
            });
        }
    }
}
