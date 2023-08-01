package com.caucraft.shadowmap.client.waypoint;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.config.WaypointConfig;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class WaypointGroup extends Waypoint {
    protected final List<Waypoint> children;
    protected boolean uiExpand;
    protected boolean expand;
    protected WaypointFilter expandFilter;
    protected DrawMode drawCollapsed;
    protected DrawMode drawExpanded;
    protected boolean autoResize;
    protected int visibleBuffer;
    protected int expandBuffer;

    public WaypointGroup(WorldWaypointManager waypointManager) {
        this(waypointManager, waypointManager.getUniqueID());
    }

    public WaypointGroup(WorldWaypointManager waypointManager, UUID id) {
        super(waypointManager, id);
        this.children = new ArrayList<>();
        this.drawCollapsed = DrawMode.POINT;
        this.drawExpanded = DrawMode.POINT;
        this.expand = true;
        this.uiExpand = true;
        this.autoResize = true;
        WaypointConfig config = ShadowMap.getInstance().getConfig().waypointConfig;
        this.visibleBuffer = config.defaultVisibleDistance.get();
        this.expandBuffer = config.defaultExpandDistance.get();
    }

    public WaypointGroup(Waypoint inheritFrom) {
        super(inheritFrom);
        this.children = new ArrayList<>();
        this.drawCollapsed = DrawMode.POINT;
        this.drawExpanded = DrawMode.POINT;
        this.expand = true;
        this.uiExpand = true;
        this.autoResize = true;
        WaypointConfig config = ShadowMap.getInstance().getConfig().waypointConfig;
        this.visibleBuffer = config.defaultVisibleDistance.get();
        this.expandBuffer = config.defaultExpandDistance.get();
        setModified(ShadowMap.getLastTickTimeS());
    }

    public List<Waypoint> getChildren() {
        return Collections.unmodifiableList(children);
    }

    void addChild(Waypoint waypoint) {
        children.add(waypoint);
        WaypointGroup oldParent = waypoint.getParent();
        if (oldParent != null) {
            oldParent.removeChild(waypoint);
        }
        waypoint.setParent(this);
        recalculatePosition();
    }

    void removeChild(Waypoint waypoint) {
        if (waypoint.getParent() != this) {
            return;
        }
        waypoint.setParent(null);
        children.remove(waypoint);
        recalculatePosition();
    }

    public boolean isExpandedAt(double x, double z, boolean ignoreFilter) {
        if (!isExpanded()) {
            return false;
        }
        WaypointFilter filter = getExpandFilter();
        if (filter == null || !filter.isEnabled() || ignoreFilter) {
            return true;
        }
        return filter.contains(MathHelper.fastFloor(x - pos.x), MathHelper.fastFloor(z - pos.z));
    }

    public boolean isExpanded() {
        return expand;
    }

    public void setExpanded(boolean expand) {
        if (this.expand != expand) {
            setModified(ShadowMap.getLastTickTimeS());
        }
        this.expand = expand;
    }

    public boolean isUiExpanded() {
        return uiExpand;
    }

    public void setUiExpanded(boolean uiExpand) {
        if (this.uiExpand != uiExpand) {
            setModified(ShadowMap.getLastTickTimeS());
        }
        this.uiExpand = uiExpand;
    }

    public WaypointFilter getExpandFilter() {
        return expandFilter;
    }

    public void setExpandFilter(WaypointFilter expandFilter, boolean updatePosition) {
        if (!Objects.equals(this.expandFilter, expandFilter)) {
            setModified(ShadowMap.getLastTickTimeS());
        }
        this.expandFilter = expandFilter;
        if (updatePosition) {
            recalculatePosition();
        }
    }

    @Override
    public void setVisibleFilter(WaypointFilter visibleFilter) {
        this.setVisibleFilter(visibleFilter, true);
    }

    public void setVisibleFilter(WaypointFilter visibleFilter, boolean updatePosition) {
        super.setVisibleFilter(visibleFilter);
        if (updatePosition) {
            recalculatePosition();
        }
    }

    public DrawMode getDrawCollapsed() {
        return drawCollapsed;
    }

    public void setDrawCollapsed(DrawMode drawCollapsed) {
        if (this.drawCollapsed != drawCollapsed) {
            setModified(ShadowMap.getLastTickTimeS());
        }
        this.drawCollapsed = Objects.requireNonNull(drawCollapsed, "Draw mode cannot be null");
    }

    public DrawMode getDrawExpanded() {
        return drawExpanded;
    }

    public void setDrawExpanded(DrawMode drawExpanded) {
        if (this.drawExpanded != drawExpanded) {
            setModified(ShadowMap.getLastTickTimeS());
        }
        this.drawExpanded = Objects.requireNonNull(drawExpanded, "Draw mode cannot be null");
    }

    public boolean isAutoResize() {
        return autoResize;
    }

    public void setAutoResize(boolean autoResize, boolean updatePosition) {
        if (this.autoResize != autoResize) {
            setModified(ShadowMap.getLastTickTimeS());
        }
        this.autoResize = autoResize;
        if (updatePosition) {
            recalculatePosition();
        }
    }

    /**
     * @return the buffer to add to the visible-filter's required radius when
     * automatically resized by a waypoint group.
     */
    public int getVisibleBuffer() {
        return visibleBuffer;
    }

    /**
     * Sets the buffer added to the radius required for the visibile-filter to
     * fit all child waypoints when automatically resized.
     * @param visibleBuffer the new buffer radius
     */
    public void setVisibleBuffer(int visibleBuffer, boolean updatePosition) {
        if (this.visibleBuffer != visibleBuffer) {
            setModified(ShadowMap.getLastTickTimeS());
        }
        if (visibleFilter != null) {
            visibleFilter.setRadius(visibleFilter.getRadius() - this.visibleBuffer + visibleBuffer);
        }
        this.visibleBuffer = visibleBuffer;
        if (updatePosition) {
            recalculatePosition();
        }
    }

    /**
     * @return the buffer to add to the visibility filter's required radius when
     * automatically resized by a waypoint group.
     */
    public int getExpandBuffer() {
        return expandBuffer;
    }

    /**
     * Sets the buffer added to the radius required for the expand-filter to
     * fit all child waypoints when automatically resized.
     * @param expandBuffer the new buffer radius
     */
    public void setExpandBuffer(int expandBuffer, boolean updatePosition) {
        if (this.expandBuffer != expandBuffer) {
            setModified(ShadowMap.getLastTickTimeS());
        }
        if (expandFilter != null) {
            expandFilter.setRadius(expandFilter.getRadius() - this.expandBuffer + expandBuffer);
        }
        this.expandBuffer = expandBuffer;
        if (updatePosition) {
            recalculatePosition();
        }
    }

    public void recalculatePosition() {
        if (!autoResize || children.isEmpty()) {
            return;
        }

        // This uses a non-recursive loop-based implementation of Welzl's Minimal Enclosing Circle algorithm
        // Ref: https://en.wikipedia.org/wiki/Smallest-circle_problem
        // Note: This treats every waypoint as a point, ignoring filter radii.

        List<Waypoint> P = children;
        List<Vector3d> R = new ArrayList<>(4);
        IntList RIndex = new IntArrayList(4);
        WelzlCircle c = new WelzlCircle();
        for (int i = P.size() - 1; i >= 0; i--) {
            Vector3d p = P.get(i).pos;
            if (c.contains(p.x, p.z)) {
                continue;
            }
            for (int j = RIndex.size() - 1; j >= 0 && RIndex.getInt(j) > i; j--) {
                RIndex.removeInt(j);
                R.remove(j);
            }
            R.add(p);
            RIndex.add(i);
            c.resize(R);
            i = P.size();
        }
        setPos(c.x, pos.y, c.y);
        int radius = MathHelper.ceil(c.r);
        if (visibleFilter != null) {
            if (visibleFilter.getRadius() != radius + visibleBuffer) {
                setModified(ShadowMap.getLastTickTimeS());
            }
            visibleFilter.setRadius(radius + visibleBuffer);
        }
        if (expandFilter != null) {
            if (expandFilter.getRadius() != radius + expandBuffer) {
                setModified(ShadowMap.getLastTickTimeS());
            }
            expandFilter.setRadius(radius + expandBuffer);
        }
    }

    @Override
    public WaypointType getType() {
        return WaypointType.GROUP;
    }

    @Override
    public void loadNbt(NbtCompound root) {
        super.loadNbt(root);
        children.clear();

        if (root.contains("expandFilter", NbtElement.COMPOUND_TYPE)) {
            expandFilter = new WaypointFilter();
            expandFilter.loadNbt(root.getCompound("expandFilter"));
        }
        try {
            drawCollapsed = DrawMode.valueOf(root.getString("drawCollapsed"));
        } catch (IllegalArgumentException ex) {
            ShadowMap.getLogger().error("Could not load collapsed draw mode for waypoint group " + getName() + " (" + getId() + ")", ex);
            drawCollapsed = DrawMode.POINT;
        }
        try {
            drawExpanded = DrawMode.valueOf(root.getString("drawExpanded"));
        } catch (IllegalArgumentException ex) {
            ShadowMap.getLogger().error("Could not load expanded draw mode for waypoint group " + getName() + " (" + getId() + ")", ex);
            drawExpanded = DrawMode.POINT;
        }
        uiExpand = !root.contains("uiExpand", NbtElement.BYTE_TYPE) || root.getBoolean("uiExpand");
        expand = !root.contains("expand", NbtElement.BYTE_TYPE) || root.getBoolean("expand");
        autoResize = !root.contains("autoResize", NbtElement.BYTE_TYPE) || root.getBoolean("autoResize");
        visibleBuffer = root.contains("visibleBuffer", NbtElement.INT_TYPE) ? root.getInt("visibleBuffer") : 100;
        expandBuffer = root.contains("expandBuffer", NbtElement.INT_TYPE) ? root.getInt("expandBuffer") : 100;
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound root = super.toNbt();
        WaypointFilter lfilter = getExpandFilter();
        if (lfilter != null) {
            root.put("expandFilter", lfilter.toNbt());
        }
        root.put("drawCollapsed", NbtString.of(getDrawCollapsed().name()));
        root.put("drawExpanded", NbtString.of(getDrawExpanded().name()));
        root.put("uiExpand", NbtByte.of(uiExpand));
        root.put("expand", NbtByte.of(expand));
        root.put("autoResize", NbtByte.of(autoResize));
        root.put("visibleBuffer", NbtInt.of(visibleBuffer));
        root.put("expandBuffer", NbtInt.of(expandBuffer));
        return root;
    }

    public enum DrawMode {
        NONE, POINT, FILTER, POINT_FILTER
    }

    private static class WelzlCircle {
        double x, y, r;

        boolean contains(double px, double py) {
            px -= x;
            py -= y;
            return px * px + py * py <= r * r;
        }

        void resize(List<Vector3d> R) {
            switch (R.size()) {
                case 0 -> x = y = r = 0;
                case 1 -> {
                    Vector3d p = R.get(0);
                    x = p.x;
                    y = p.z;
                    r = 1;
                }
                case 2 -> {
                    // Midpoint of diameter
                    Vector3d a = R.get(0);
                    Vector3d b = R.get(1);
                    x = (a.x + b.x) / 2;
                    y = (a.z + b.z) / 2;
                    double dx = a.x - b.x;
                    double dy = a.z - b.z;
                    r = 0.01 + 0.5 * Math.sqrt(dx * dx + dy * dy);
                }
                case 3 -> {
                    // Simplified lineq/matrix solver using lines perpendicular to
                    // the midpoints of AB and BC to find the center of the circle
                    Vector3d a = R.get(0);
                    Vector3d b = R.get(1);
                    Vector3d c = R.get(2);
                    double x1 = b.x - a.x;
                    double y1 = b.z - a.z;
                    double z1 = ((a.x + b.x) * x1 + (a.z + b.z) * y1) * 0.5F;
                    double x2 = c.x - b.x;
                    double y2 = c.z - b.z;
                    double z2 = ((b.x + c.x) * x2 + (b.z + c.z) * y2) * 0.5F;
                    if (x1 == 0) {
                        if (x2 == 0) {
                            throw new IllegalArgumentException("Retained triangle points are co-linear: " + R);
                        }
                        double multi = x1 / x2;
                        y1 -= y2 * multi;
                        if (y1 == 0) {
                            throw new IllegalArgumentException("Retained triangle points are co-linear: " + R);
                        }
                        z1 -= z2 * multi;
                        z2 -= z1 * y2 / y1;

                        x = z2 / x2;
                        y = z1 / y1;
                        double dx = a.x - x;
                        double dy = a.z - y;
                        r = 0.01 + Math.sqrt(dx * dx + dy * dy);
                    } else {
                        double multi = x2 / x1;
                        y2 -= y1 * multi;
                        if (y2 == 0) {
                            throw new IllegalArgumentException("Retained triangle points are co-linear: " + R);
                        }
                        z2 -= z1 * multi;
                        z1 -= z2 * y1 / y2;

                        x = z1 / x1;
                        y = z2 / y2;
                        double dx = a.x - x;
                        double dy = a.z - y;
                        r = 0.01 + Math.sqrt(dx * dx + dy * dy);
                    }
                }
                default -> throw new IllegalArgumentException("Retained size cannot exceed 3: " + R);
            }
        }
    }
}
