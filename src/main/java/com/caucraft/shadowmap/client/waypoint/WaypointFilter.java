package com.caucraft.shadowmap.client.waypoint;

import com.caucraft.shadowmap.client.ShadowMap;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.MathHelper;

import java.util.Objects;

public class WaypointFilter {
    private boolean enabled;
    private int radius;
    private Shape shape;

    public WaypointFilter() {
        this.enabled = true;
        this.shape = Shape.CIRCLE;
        this.radius = 100;
    }

    public WaypointFilter(WaypointFilter inheritFrom) {
        if (inheritFrom == null) {
            this.enabled = true;
            this.shape = Shape.CIRCLE;
            this.radius = 100;
        } else {
            this.enabled = inheritFrom.enabled;
            this.radius = inheritFrom.radius;
            this.shape = inheritFrom.shape;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return The filter radius. For polygonal shapes, this is the radius of an
     * <i>inscribed</i> circle.
     */
    public int getRadius() {
        return radius;
    }

    /**
     * Sets the filter's radius. This value is automatically clamped between 0
     * and 85 million (slightly larger than the distance from one corner of the
     * world to the other).
     * @param radius the new filter radius
     */
    public void setRadius(int radius) {
        this.radius = MathHelper.clamp(radius, 0, 85_000_000);
    }

    public Shape getShape() {
        return shape;
    }

    public void setShape(Shape shape) {
        this.shape = Objects.requireNonNull(shape, "Shape cannot be null");
    }

    /**
     * Tests if the waypoint filter contains an x/z coordinate pair. The
     * coordinates provided should be an offset from the waypoint's center.
     * @param xOffset offset of the test X coordinate from the waypoint's
     * center X coordinate
     * @param zOffset offset of the test Z coordinate from the waypoint's
     * center Z coordinate
     * @return true if the filter's shape and radius contain the coordinates
     * relative to the waypoint's center, false otherwise
     */
    public boolean contains(int xOffset, int zOffset) {
        return getShape().coordTest.test(xOffset, zOffset, radius);
    }

    public void loadNbt(NbtCompound root) {
        enabled = root.getBoolean("enabled");
        radius = root.getInt("radius");
        try {
            shape = Shape.valueOf(root.getString("shape"));
        } catch (IllegalArgumentException ex) {
            ShadowMap.getLogger().error("Could not load shape for waypoint filter: " + root.getString("shape"), ex);
            shape = Shape.CIRCLE;
        }
    }

    public NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();
        root.put("enabled", NbtByte.of(enabled));
        root.put("radius", NbtInt.of(radius));
        root.put("shape", NbtString.of(shape.name()));
        return root;
    }

    public enum Shape { // TODO look for all usages of this and implement non-circle stuff
        CIRCLE((x, z, r) -> (long) x * (long) x + (long) z * (long) z < (long) r * (long)r),
        SQUARE((x, z, r) -> Math.abs(x) < r && Math.abs(z) < r),
        OCTAGON((x, z, r) -> Math.abs(x) < r && Math.abs(z) < r && Math.abs(x) + Math.abs(z) < (long) r * 99 / 70);

        final ShapePredicate coordTest;

        Shape(ShapePredicate coordTest) {
            this.coordTest = coordTest;
        }
    }

    @FunctionalInterface
    public interface ShapePredicate {
        boolean test(int x, int z, int radius);
    }
}
