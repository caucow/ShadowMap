package com.caucraft.shadowmap.client.waypoint;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.config.WaypointConfig;
import com.caucraft.shadowmap.client.util.data.DeletableLiveDataMap;
import net.minecraft.nbt.NbtCompound;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class WorldWaypointManager {

    private long saved;
    private long modified;
    private DeletableLiveDataMap<Waypoint> allWaypoints; // todo rename to "allWaypoints"
    private Map<UUID, Waypoint> rootWaypoints;

    public WorldWaypointManager() {
        this.allWaypoints = new DeletableLiveDataMap<>((id, nbtRoot) -> {
            if (nbtRoot == null) {
                return new Waypoint(this, id);
            }
            Waypoint.WaypointType type;
            try {
                type = Waypoint.WaypointType.valueOf(nbtRoot.getString(Waypoint.TYPE_KEY));
            } catch (IllegalArgumentException ex) {
                ShadowMap.getLogger().warn("Illegal waypoint type \"" + nbtRoot.getString(Waypoint.TYPE_KEY) + "\" will revert to a normal waypoint", ex);
                type = Waypoint.WaypointType.POINT;
            }
            return switch (type) {
                case POINT -> new Waypoint(this, id);
                case GROUP -> new WaypointGroup(this, id);
            };
        });
        this.rootWaypoints = new LinkedHashMap<>();
    }

    /**
     * @return a new UUID that does not map to a waypoint in this world. This
     * method will randomly generate ids until there is not a collision. The
     * returned UUID is not added to the internal map until a waypoint with that
     * id is added.
     */
    public UUID getUniqueID() {
        UUID id;
        while (allWaypoints.containsKey(id = UUID.randomUUID())) {}
        return id;
    }

    /**
     * Adds a waypoint to, or moves a waypoint within, the world's waypoint
     * storage.
     *
     * @param waypoint the waypoint to add or move
     * @param groupId id of the new parent waypoint, or {@code null} to move the
     * waypoint to the root group.
     *
     * @throws IllegalArgumentException if the waypoint has the same ID as an
     * existing waypoint in this world, but the two are not the same waypoint;
     * if the parent waypoint ID does not exist in this map.
     */
    public void addOrMoveWaypoint(Waypoint waypoint, UUID groupId) {
        if (waypoint.waypointManager != null && waypoint.waypointManager != this) {
            throw new IllegalArgumentException("Waypoint belongs to another world: " + waypoint);
        }
        Waypoint existing = allWaypoints.putIfAbsent(waypoint.getId(), waypoint);
        if (existing != null && existing != waypoint) {
            throw new IllegalArgumentException(
                    "Added waypoint has the same ID as an existing waypoint: " + waypoint);
        }
        setModified(ShadowMap.getLastTickTimeS());
        if (groupId == null) {
            WaypointGroup parent = waypoint.getParent();
            if (parent != null) {
                parent.removeChild(waypoint);
            }
            rootWaypoints.put(waypoint.getId(), waypoint);
        } else {
            Waypoint parent = allWaypoints.get(groupId);
            if (parent == null) {
                throw new IllegalArgumentException("Group waypoint ID does not exist: " + groupId);
            }
            if (parent == waypoint) {
                throw new IllegalArgumentException("Adding waypoint " + waypoint + " to group " + parent + " would create a loop");
            }
            WaypointGroup grandparent = parent.getParent();
            while (grandparent != null) {
                if (grandparent == waypoint) {
                    throw new IllegalArgumentException("Adding waypoint " + waypoint + " to group " + parent + " would create a loop");
                }
                grandparent = grandparent.getParent();
            }
            if (!(parent instanceof WaypointGroup)) {
                grandparent = parent.getParent();
                if (grandparent != null) {
                    grandparent.removeChild(parent);
                }
                parent = new WaypointGroup(parent);
                if (grandparent != null) {
                    grandparent.addChild(parent);
                } else {
                    rootWaypoints.put(parent.getId(), parent);
                }
                allWaypoints.put(parent.getId(), parent);
            }
            ((WaypointGroup) parent).addChild(waypoint);
            rootWaypoints.remove(waypoint.getId());
        }
    }

    /**
     * Removes a waypoint from the world's waypoint storage, optionally moving
     * any waypoints it contains (if it is a waypoint group) to its parent
     * group or the root group (if it is a root waypoint).
     * @param waypoint the waypoint to remove
     * @param moveChildrenToParent whether to shift child waypoints to the
     * group's parent or delete them too.
     */
    public void removeWaypoint(Waypoint waypoint, boolean moveChildrenToParent) {
        if (waypoint.waypointManager != this) {
            throw new IllegalArgumentException("Waypoint does not belong to this world: " + waypoint);
        }
        if (!allWaypoints.remove(waypoint.getId(), waypoint)) {
            throw new IllegalArgumentException("Tried to remove different waypoint with same ID: " + waypoint);
        }
        setModified(ShadowMap.getLastTickTimeS());
        WaypointGroup parent = waypoint.getParent();
        if (parent != null) {
            parent.removeChild(waypoint);
        } else {
            rootWaypoints.remove(waypoint.getId());
        }
        if (waypoint instanceof WaypointGroup group) {
            if (moveChildrenToParent) {
                if (parent != null) {
                    for (Waypoint child : new ArrayList<>(group.getChildren())) {
                        parent.addChild(child);
                    }
                } else {
                    for (Waypoint child : group.getChildren()) {
                        rootWaypoints.put(child.getId(), child);
                        child.setParent(null);
                    }
                }
            } else {
                Deque<Waypoint> waypointQueue = new ArrayDeque<>(group.getChildren());
                while (!waypointQueue.isEmpty()) {
                    Waypoint next = waypointQueue.poll();
                    next.setParent(null);
                    if (next instanceof WaypointGroup childGroup) {
                        waypointQueue.addAll(childGroup.getChildren());
                    }
                    allWaypoints.remove(next.getId());
                }
            }
        }
    }

    public void setModified(long modified) {
        this.modified = modified;
    }

    public long getSaved() {
        return saved;
    }

    public void setSaved(long saved) {
        this.saved = saved;
    }

    public boolean isModified() {
        return modified >= saved;
    }

    public Optional<Waypoint> getWaypoint(UUID id) {
        return Optional.ofNullable(allWaypoints.get(id));
    }

    public Optional<WaypointGroup> getWaypointGroup(UUID id) {
        Waypoint waypoint = allWaypoints.get(id);
        return waypoint instanceof WaypointGroup group ? Optional.of(group) : Optional.empty();
    }

    public List<Waypoint> getWaypoints() {
        return new ArrayList<>(allWaypoints.values());
    }

    public List<Waypoint> getWaypoints(String name) {
        List<Waypoint> matchingNames = new ArrayList<>();
        for (Map.Entry<UUID, Waypoint> entry : allWaypoints.entrySet()) {
            if (entry.getValue().getName().equals(name)) {
                matchingNames.add(entry.getValue());
            }
        }
        return matchingNames;
    }

    public List<WaypointGroup> getWaypointGroups(String name) {
        List<WaypointGroup> matchingNames = new ArrayList<>();
        for (Map.Entry<UUID, Waypoint> entry : allWaypoints.entrySet()) {
            Waypoint wp = entry.getValue();
            if (wp instanceof WaypointGroup group && group.getName().equals(name)) {
                matchingNames.add(group);
            }
        }
        return matchingNames;
    }

    /**
     * Gets an Optional of the UUID of a group or waypoint to be used as the
     * Deaths group. If a group exists with the
     * {@link WaypointConstants#DEATHS_GROUP_ID} ID, that UUID will be returned.
     * Otherwise, if there exists a group with the default deaths group name,
     * its UUID will be returned (preferring groups without a parent).
     * Otherwise, if there exists a waypoint with the default death group name,
     * its UUID will be returned (and upon adding a waypoint with this as the
     * groupID, it will be converted to a group).
     * If no group or point matches these criteria, the returned optional will
     * be empty.
     * @param config The current waypoint config
     * @return an optional with the ID of the expected deaths group.
     */
    public Optional<UUID> getDeathsGroupId(WaypointConfig config) {
        Optional<UUID> deathGroupOptional = getWaypointGroup(WaypointConstants.DEATHS_GROUP_ID).map(Waypoint::getId);
        String deathGroupName = config.defaultDeathGroupName.get();
        if (deathGroupOptional.isEmpty()) {
            List<WaypointGroup> groups = getWaypointGroups(deathGroupName);
            deathGroupOptional = deathGroupOptional
                    .or(() -> groups.stream().filter((group) -> group.getParent() == null).findFirst().map(Waypoint::getId))
                    .or(() -> groups.stream().findFirst().map(Waypoint::getId));
        }
        if (deathGroupOptional.isEmpty()) {
            List<Waypoint> points = getWaypoints(deathGroupName);
            deathGroupOptional = deathGroupOptional
                    .or(() -> points.stream().filter((point) -> point.getParent() == null).findFirst().map(Waypoint::getId))
                    .or(() -> points.stream().findFirst().map(Waypoint::getId));
        }
        return deathGroupOptional;
    }

    /**
     * @return an unmodifiable collection of all root waypoints
     */
    public Collection<Waypoint> getRootWaypoints() {
        List<Waypoint> waypoints = new ArrayList<>(rootWaypoints.size());
        for (Map.Entry<UUID, Waypoint> entry : rootWaypoints.entrySet()) {
            Waypoint root = entry.getValue();
            Waypoint main = allWaypoints.get(entry.getKey());
            if (root != main) {
                throw new IllegalArgumentException("Waypoint in rootWaypoints does not match waypoint in allWaypoints: " + root + " != " + main);
            }
            waypoints.add(root);
        }
        return waypoints;
    }

    /**
     * Adds all root waypoints to the provided collection.
     * @param collection destination collection for root waypoints.
     */
    public void getRootWaypoints(Collection<Waypoint> collection) {
        for (Map.Entry<UUID, Waypoint> entry : rootWaypoints.entrySet()) {
            Waypoint root = entry.getValue();
            Waypoint main = allWaypoints.get(entry.getKey());
            if (root != main) {
                throw new IllegalArgumentException("Waypoint in rootWaypoints does not match waypoint in allWaypoints: " + root + " != " + main);
            }
            collection.add(root);
        }
    }

    public void loadNbt(NbtCompound root) throws IOException {
        long newModified = 0;

        rootWaypoints.clear();

        allWaypoints.loadNbt(root);

        nextEntry: for (var entry : allWaypoints.entrySet()) {
            UUID id = entry.getKey();
            Waypoint waypoint = entry.getValue();
            newModified = Math.max(newModified, waypoint.getModified());
            UUID parentId = waypoint.getParentId();
            if (parentId == null) {
                rootWaypoints.put(id, waypoint);
                continue;
            }
            if (!allWaypoints.containsKey(parentId)) {
                ShadowMap.getLogger().warn("Parent id of waypoint does not exist; waypoint will be orphaned: " + parentId + " -x-> " + waypoint.getId());
                waypoint.setParent(null);
                rootWaypoints.put(waypoint.getId(), waypoint);
                continue;
            }
            Waypoint parent = allWaypoints.get(parentId);
            if (parent == waypoint) {
                ShadowMap.getLogger().warn("Waypoint " + waypoint + " in group " + parent + " would create a loop and will be orphaned.");
                waypoint.setParent(null);
                rootWaypoints.put(waypoint.getId(), waypoint);
                continue;
            }
            WaypointGroup grandparent = parent.getParent();
            while (grandparent != null) {
                if (grandparent == waypoint) {
                    ShadowMap.getLogger().warn("Waypoint " + waypoint + " in group " + parent + " would create a loop and will be orphaned.");
                    waypoint.setParent(null);
                    rootWaypoints.put(waypoint.getId(), waypoint);
                    continue nextEntry;
                }
                grandparent = grandparent.getParent();
            }
            if (!(parent instanceof WaypointGroup)) {
                grandparent = parent.getParent();
                if (grandparent != null) {
                    grandparent.removeChild(parent);
                }
                parent = new WaypointGroup(parent);
                if (grandparent != null) {
                    grandparent.addChild(parent);
                } else {
                    rootWaypoints.put(parent.getId(), parent);
                }
                allWaypoints.put(parent.getId(), parent);
            }
            ((WaypointGroup) parent).addChild(waypoint);
        }

        this.modified = newModified;
    }

    public NbtCompound toNbt() {
        NbtCompound root = allWaypoints.toNbt();
        return root;
    }
}
