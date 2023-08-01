package com.caucraft.shadowmap.api.ui;

import com.caucraft.shadowmap.api.map.MapWorld;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2d;
import org.joml.Matrix3x2dc;
import org.joml.Matrix4f;
import org.joml.Vector2d;

import java.util.Objects;

public abstract class MapRenderContext {

    protected final Vector2d tempVector;

    /**
     * A matrix used to transform scaled UI space positions to screen space.
     */
    public final Matrix3x2dc uiToScreen;

    /**
     * A matrix used to transform screen space positions to scaled UI space.
     */
    public final Matrix3x2dc screenToUi;

    /**
     * A matrix used to transform world/block positions to screen space.
     */
    public final Matrix3x2dc worldToScreen;

    /**
     * A matrix used to transform screen space positions to world space.
     */
    public final Matrix3x2dc screenToWorld;

    /**
     * A matrix that can transform scaled UI space positions to world space.
     */
    public final Matrix3x2dc uiToWorld;

    /**
     * A matrix that can transform world space positions to scaled UI space.
     */
    public final Matrix3x2dc worldToUi;

    /**
     * A matrix that will transform a point based on the context's rotation.
     */
    protected final Matrix3x2dc rotationMatrix;

    /**
     * A matrix that will reverse-transform a point based on the context's
     * rotation.
     */
    protected final Matrix3x2dc inverseRotationMatrix;

    /**
     * Minecraft's render tessellator.
     */
    public final Tessellator tessellator;

    /**
     * Minecraft's render tessellator's buffer builder. This is equivalent to
     * {@link #tessellator}{@code .getBuffer()}. Convenience methods
     * {@link #worldVertex(double, double, double)} and
     * {@link #uiVertex(double, double, double)} are provided to quickly provide
     * vertex coordinates.
     */
    public final BufferBuilder buffer;

    /**
     * THe type of map being rendered, either fullscreen or one of the minimap
     * shapes.
     */
    public final MapType mapType;

    /**
     * The world currently shown on the map.
     */
    public final MapWorld world;

    /**
     * RectangleD describing the bounds of the world visible on the map. The
     * blocks at (x1, z1) and (x2, z2) may be rendered. (x1, z1) are the most
     * negative edges and (x2, z2) the most positive. This may differ from the
     * world-translated and -scaled UI bounds, especially in the case of a
     * rectangular map window with non-zero rotation.
     */
    public final RectangleD worldBounds;

    /**
     * RectangleD describing the bounds of regions visible on the map. The
     * regions at (x1, z1) and (x2, z2) may be rendered. (x1, z1) are the most
     * negative edges and (x2, z2) the most positive.
     */
    public final RectangleI regionBounds;

    /**
     * RectangleD describing the map's bounds in the UI (UI space). This is
     * translated so that the center of the map is at (0, 0).
     * (x1, z1) are the most negative edges and (x2, z2) the most positive.
     */
    public final RectangleD uiBounds;

    /**
     * The radius of blocks visible on the map. If the map is rectangular, this
     * follows the mathematical definition (center to corner).
     */
    public final double worldRadius;

    /**
     * If radius of pixels (screen scale) visible on the map. If the map is
     * rectangular, this follows the mathematical definition (center to corner).
     */
    public final double uiRadius;

    /** The UI scale factor. */
    public final double uiScale;

    /** The game window's width in pixels (screen space). */
    public final int windowWidth;

    /** The game window's height in pixels (screen space). */
    public final int windowHeight;

    /** The cursor's X position in pixels (screen space), if present. */
    public final double mouseXScreen;

    /** The cursor's Y position in pixels (screen space), if present. */
    public final double mouseYScreen;

    /** The cursor's X position in UI pixels (UI space), if present. */
    public final double mouseXUi;

    /** The cursor's Y position in UI pixels (UI space), if present. */
    public final double mouseYUi;

    /** The cursor's X position in blocks (world space), if present. */
    public final double mouseXWorld;

    /** The cursor's Z position in blocks (world space), if present. */
    public final double mouseZWorld;

    /** Center world X coordinate */
    public final double centerX;

    /** Center world Z coordinate */
    public final double centerZ;

    /**
     * Current map zoom (framebuffer pixels per block). Map zoom is fully
     * independent from UI scale.
     */
    public final double zoom;

    /**
     * Current map rotation (in radians).
     */
    public final double rotation;

    /**
     * The system time at the beginning of this map component render, in
     * milliseconds.
     */
    public final long time;

    MapRenderContext(Builder builder) {
        this.tempVector = new Vector2d();

        Matrix3x2d uiToScreen = new Matrix3x2d();
        uiToScreen.scale(builder.uiScale);
        uiToScreen.translate(builder.uiMapCenterX, builder.uiMapCenterY);
        this.uiToScreen = uiToScreen;
        Matrix3x2d screenToUi = new Matrix3x2d();
        screenToUi.translate(-builder.uiMapCenterX, -builder.uiMapCenterY);
        screenToUi.scale(1 / builder.uiScale);
        this.screenToUi = screenToUi;

        Matrix3x2d worldToScreen = new Matrix3x2d();
        // Bias slightly by a millionth of a <unit> to account for imprecision
        // that leads to undesirable X.9999999999s that floor() to a visually
        // incorrect number
        worldToScreen.translate(builder.uiMapCenterX * builder.uiScale, builder.uiMapCenterY * builder.uiScale);
        worldToScreen.scale(builder.zoom);
        worldToScreen.rotate(builder.rotation);
        worldToScreen.translate(-builder.centerX + 0.000001, -builder.centerZ + 0.000001);
        this.worldToScreen = worldToScreen;
        Matrix3x2d screenToWorld = new Matrix3x2d();
        screenToWorld.translate(builder.centerX, builder.centerZ);
        screenToWorld.rotate(-builder.rotation);
        screenToWorld.scale(1 / builder.zoom);
        screenToWorld.translate(-builder.uiMapCenterX * builder.uiScale + 0.000001, -builder.uiMapCenterY * builder.uiScale + 0.000001);
        this.screenToWorld = screenToWorld;

        this.worldToUi = new Matrix3x2d(screenToUi).mul(worldToScreen);
        this.uiToWorld = new Matrix3x2d(screenToWorld).mul(uiToScreen);

        this.mouseXScreen = builder.mouseXScreen;
        this.mouseYScreen = builder.mouseYScreen;
        tempVector.set(mouseXScreen, mouseYScreen);
        screenToUi.transformPosition(tempVector);
        this.mouseXUi = tempVector.x;
        this.mouseYUi = tempVector.y;
        tempVector.set(mouseXScreen, mouseYScreen);
        screenToWorld.transformPosition(tempVector);
        this.mouseXWorld = tempVector.x;
        this.mouseZWorld = tempVector.y;
        tempVector.zero();

        this.rotationMatrix = new Matrix3x2d().rotateAbout(builder.rotation, builder.centerX, builder.centerZ);
        this.inverseRotationMatrix = new Matrix3x2d(rotationMatrix).invert();

        this.tessellator = Tessellator.getInstance();
        this.buffer = tessellator.getBuffer();
        this.mapType = builder.mapType;
        this.world = builder.world;

        RectangleD uiBounds = this.uiBounds = builder.uiBounds;
        if (mapType == MapType.MINIMAP_CIRCULAR) {
            this.uiRadius = uiBounds.wdiv2;
        } else {
            this.uiRadius = Math.sqrt(uiBounds.wdiv2 * uiBounds.wdiv2 + uiBounds.hdiv2 * uiBounds.hdiv2);
        }
        this.worldRadius = uiRadius * builder.uiScale / builder.zoom;
        RectangleD worldBounds = this.worldBounds = new RectangleD(
                builder.centerX - worldRadius,
                builder.centerZ - worldRadius,
                builder.centerX + worldRadius,
                builder.centerZ + worldRadius
        );
        this.regionBounds = new RectangleI(
                MathHelper.floor(worldBounds.x1) >> 9,
                MathHelper.floor(worldBounds.z1) >> 9,
                MathHelper.ceil(worldBounds.x2) >> 9,
                MathHelper.ceil(worldBounds.z2) >> 9
        );

        this.uiScale = builder.uiScale;
        this.windowWidth = builder.windowWidth;
        this.windowHeight = builder.windowHeight;
        this.centerX = builder.centerX;
        this.centerZ = builder.centerZ;
        this.zoom = builder.zoom;
        this.rotation = builder.rotation;
        this.time = builder.time;
    }

    /**
     * Determines whether the point in the world is within the bounds of the
     * map.
     * @param x x coordinate of the point in world coordinates
     * @param z z coordinate of the point in world coordinates
     * @return true if the point is within (or on the edge of) the bounds of
     * the map.
     */
    public abstract boolean worldContains(double x, double z);

    /**
     * Determines whether the circle in the world is completely within the
     * bounds of the map.
     * @param x x coordinate of the circle center in world coordinates
     * @param z z coordinate of the circle center in world coordinates
     * @param worldRadius size of the circle to test
     * @return true if the circle is entirely within (or touching the edge
     * of) the map.
     */
    public abstract boolean worldContains(double x, double z, double worldRadius);

    /**
     * Determines if any portion of the circle in the world is within the bounds
     * of the map.
     * @param x x coordinate of the circle center in world coordinates
     * @param z z coordinate of the circle center in world coordinates
     * @param worldRadius size of the circle to test in world coordinates
     * @return true if any part of the circle intersects the map.
     */
    public abstract boolean worldIntersectsCircle(double x, double z, double worldRadius);

    /**
     * Finds a point in the direction of (x, z) from the map center that is
     * {@code worldEdgeBuffer} blocks from the edge of the map, placing the
     * resulting values in the provided vector. Useful for placing directional
     * indicators for features such as waypoints.
     * @param x x coordinate of target point in world coordinates
     * @param z z coordinate of target point in world coordinates
     * @param worldEdgeBuffer the distance from the edge of the map in blocks to
     * the resulting point.
     * @param result vector to place the resulting point in. The vector's x and
     * y coordinates will be set to the x and z UI coordinates respectively of
     * the result point.
     */
    public abstract void worldPointTowardsEdge(double x, double z, double worldEdgeBuffer, Vector2d result);

    /**
     * Determines whether the point in the UI is within the bounds of the map.
     * @param x x coordinate of the point in UI coordinates
     * @param z z coordinate of the point in UI coordinates
     * @return true if the point is within (or on the edge of) the bounds of
     * the map.
     */
    public abstract boolean uiContains(double x, double z);

    /**
     * Determines whether the circle in the UI is completely within the bounds
     * of the map.
     * @param x x coordinate of the circle center in UI coordinates
     * @param z z coordinate of the circle center in UI coordinates
     * @param uiRadius size of the circle to test in UI coordinates
     * @return true if the circle is entirely within (or touching the edge
     * of) the map.
     */
    public abstract boolean uiContains(double x, double z, double uiRadius);

    /**
     * Determines if any portion of the circle in the UI is within the bounds of
     * the map.
     * @param x x coordinate of the circle center in UI coordinates
     * @param z z coordinate of the circle center in UI coordinates
     * @param uiRadius size of the circle to test in UI coordinates
     * @return true if any part of the circle intersects the map.
     */
    public abstract boolean uiIntersectsCircle(double x, double z, double uiRadius);

    /**
     * Finds a point in the direction of (x, z) from the map center that is {@code uiEdgeBuffer} blocks from the edge of
     * the map, placing the resulting values in the provided vector. Useful for placing directional indicators for
     * features such as waypoints.
     *
     * @param x x coordinate of target point in UI coordinates
     * @param z z coordinate of target point in UI coordinates
     * @param uiEdgeBuffer the distance from the edge of the map in UI pixels to the resulting point.
     * @param result vector to place the resulting point in. The vector's x and y coordinates will be set to the x and z
     * world coordinates respectively of the result point.
     */
    public abstract void uiPointTowardsEdge(double x, double z, double uiEdgeBuffer, Vector2d result);

    /**
     * Analogous to {@link BufferBuilder#vertex(Matrix4f, float, float, float)},
     * using {@link #worldToScreen} as the transformation argument.
     * @param x x coordinate of the vertex in world space
     * @param z z coordinate of the vertex in world space
     * @param depth render depth in the map/ui framebuffer
     * @return the context's {@link #buffer BufferBuilder}
     */
    public VertexConsumer worldVertex(double x, double z, double depth) {
        Vector2d vec = tempVector.set(x, z);
        worldToScreen.transformPosition(vec);
        return buffer.vertex(vec.x, vec.y, depth);
    }

    /**
     * Analogous to {@link BufferBuilder#vertex(Matrix4f, float, float, float)},
     * using {@link #uiToScreen} as the transformation argument.
     * @param x x coordinate of the vertex in scaled UI space
     * @param y y coordinate of the vertex in scaled UI space
     * @param depth render depth in the map/ui framebuffer
     * @return the context's {@link #buffer BufferBuilder}
     */
    public VertexConsumer uiVertex(double x, double y, double depth) {
        Vector2d vec = tempVector.set(x, y);
        uiToScreen.transformPosition(vec);
        return buffer.vertex(vec.x, vec.y, depth);
    }

    public VertexConsumer vertex(Matrix3x2dc matrix, double x, double y, double depth) {
        Vector2d vec = tempVector.set(x, y);
        matrix.transformPosition(vec);
        return buffer.vertex(vec.x, vec.y, depth);
    }

    public Matrix4f applyWorldMatrix(Matrix4f mat3d) {
        Matrix3x2dc mat2 = this.worldToScreen;
        return mat3d.set(
                (float) mat2.m00(), (float) mat2.m01(), 0.0F, 0.0F,
                (float) mat2.m10(), (float) mat2.m11(), 0.0F, 0.0F,
                0.0F, 0.0F, 1.0F, 0.0F,
                (float) mat2.m20(), (float) mat2.m21(), 0.0F, 1.0F
        );
    }

    public Matrix4f applyUiMatrix(Matrix4f mat3d) {
        Matrix3x2dc mat2 = this.uiToScreen;
        return mat3d.set(
                (float) mat2.m00(), (float) mat2.m01(), 0.0F, 0.0F,
                (float) mat2.m10(), (float) mat2.m11(), 0.0F, 0.0F,
                0.0F, 0.0F, 1.0F, 0.0F,
                (float) mat2.m20(), (float) mat2.m21(), 0.0F, 1.0F
        );
    }

    public enum MapType {
        FULLSCREEN, MINIMAP_RECTANGULAR, MINIMAP_CIRCULAR;
    }

    public static final class RectangleD {
        public final double x1, z1, x2, z2, w, h, wdiv2, hdiv2;

        public RectangleD(double x1, double z1, double x2, double z2) {
            this.x1 = x1;
            this.z1 = z1;
            this.x2 = x2;
            this.z2 = z2;
            double w = this.w = x2 - x1 + 1;
            double h = this.h = z2 - z1 + 1;
            this.wdiv2 = w / 2;
            this.hdiv2 = h / 2;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (RectangleD) obj;
            return this.x1 == that.x1 && this.z1 == that.z1 && this.x2 == that.x2 && this.z2 == that.z2;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x1, z1, x2, z2);
        }

        @Override
        public String toString() {
            return "RectangleD[(" + x1 + "," + z1 + ") -> (" + x2 + "," + z2 + ")]";
        }
    }

    public static final class RectangleI {
        public final int x1, z1, x2, z2, w, h, wdiv2, hdiv2;

        public RectangleI(int x1, int z1, int x2, int z2) {
            this.x1 = x1;
            this.z1 = z1;
            this.x2 = x2;
            this.z2 = z2;
            int w = this.w = x2 - x1 + 1;
            int h = this.h = z2 - z1 + 1;
            this.wdiv2 = w / 2;
            this.hdiv2 = h / 2;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (RectangleI) obj;
            return this.x1 == that.x1 && this.z1 == that.z1 && this.x2 == that.x2 && this.z2 == that.z2;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x1, z1, x2, z2);
        }

        @Override
        public String toString() {
            return "RectangleI[(" + x1 + "," + z1 + ") -> (" + x2 + "," + z2 + ")]";
        }
    }

    // literally just to avoid a context constructor with 100 parameters
    // this is effectively private use anyways
    public static class Builder {
        MapWorld world;
        MapType mapType;
        long time;
        RectangleD uiBounds;
        int windowWidth, windowHeight;
        double uiMapCenterX, uiMapCenterY, uiScale, centerX, centerZ, zoom, rotation, mouseXScreen, mouseYScreen;

        public Builder(MapWorld world, MapType mapType) {
            this.world = world;
            this.mapType = mapType;
            this.time = System.currentTimeMillis();
        }

        public void setMouse(double mouseXScreen, double mouseYScreen) {
            this.mouseXScreen = mouseXScreen;
            this.mouseYScreen = mouseYScreen;
        }

        public void setTransforms(double uiMapCenterX, double uiMapCenterY, double uiScale, int windowWidth, int windowHeight) {
            this.uiMapCenterX = uiMapCenterX;
            this.uiMapCenterY = uiMapCenterY;
            this.uiScale = uiScale;
            this.windowWidth = windowWidth;
            this.windowHeight = windowHeight;
        }

        public void setUiSize(RectangleD uiBounds) {
            this.uiBounds = uiBounds;
        }

        public void setView(double centerX, double centerZ, double zoom, double rotation) {
            this.centerX = Math.floor(centerX * zoom) / zoom;
            this.centerZ = Math.floor(centerZ * zoom) / zoom;
            this.zoom = zoom;
            this.rotation = rotation;
        }

        public MapRenderContext getContext() {
            return switch (mapType) {
                case FULLSCREEN, MINIMAP_RECTANGULAR -> new RectangularMapRenderContext(this);
                case MINIMAP_CIRCULAR -> new CircularMapRenderContext(this);
            };
        }
    }

    public static class CircularMapRenderContext extends MapRenderContext {

        CircularMapRenderContext(Builder builder) {
            super(builder);
        }

        @Override
        public boolean worldContains(double x, double z) {
            x = x - centerX;
            z = z - centerZ;
            double r = this.worldRadius;
            return x * x + z * z <= r * r;
        }

        @Override
        public boolean worldContains(double x, double z, double worldRadius) {
            x = x - centerX;
            z = z - centerZ;
            double r = this.worldRadius - worldRadius;
            return x * x + z * z <= r * r;
        }

        @Override
        public boolean worldIntersectsCircle(double x, double z, double worldRadius) {
            x = x - centerX;
            z = z - centerZ;
            double r = this.worldRadius + worldRadius;
            return x * x + z * z <= r * r;
        }

        @Override
        public void worldPointTowardsEdge(double x, double z, double worldEdgeBuffer, Vector2d result) {
            x = x - centerX;
            z = z - centerZ;
            double rScale = (this.worldRadius - worldEdgeBuffer) * MathHelper.fastInverseSqrt(x * x + z * z);
            result.set(centerX + x * rScale, centerZ + z * rScale);
        }

        @Override
        public boolean uiContains(double x, double z) {
            double r = this.uiRadius;
            return x * x + z * z <= r * r;
        }

        @Override
        public boolean uiContains(double x, double z, double uiRadius) {
            double r = this.uiRadius - uiRadius;
            return x * x + z * z <= r * r;
        }

        @Override
        public boolean uiIntersectsCircle(double x, double z, double uiRadius) {
            double r = this.uiRadius + uiRadius;
            return x * x + z * z <= r * r;
        }

        @Override
        public void uiPointTowardsEdge(double x, double z, double uiEdgeBuffer, Vector2d result) {
            double rScale = (this.uiRadius - uiEdgeBuffer) * MathHelper.fastInverseSqrt(x * x + z * z);
            result.set(x * rScale, z * rScale);
        }
    }

    public static class RectangularMapRenderContext extends MapRenderContext {

        RectangularMapRenderContext(Builder builder) {
            super(builder);
        }

        @Override
        public boolean worldContains(double x, double z) {
            Vector2d tempVector = this.tempVector;
            tempVector.set(x, z);
            worldToUi.transformPosition(tempVector);
            return uiContains(tempVector.x, tempVector.y);
        }

        @Override
        public boolean worldContains(double x, double z, double worldRadius) {
            Vector2d tempVector = this.tempVector;
            tempVector.set(x, z);
            worldToUi.transformPosition(tempVector);
            return uiContains(tempVector.x, tempVector.y, worldRadius * zoom / uiScale);
        }

        @Override
        public boolean worldIntersectsCircle(double x, double z, double worldRadius) {
            Vector2d tempVector = this.tempVector;
            tempVector.set(x, z);
            worldToUi.transformPosition(tempVector);
            return uiIntersectsCircle(tempVector.x, tempVector.y, worldRadius * zoom / uiScale);
        }

        @Override
        public void worldPointTowardsEdge(double x, double z, double worldEdgeBuffer, Vector2d result) {
            result.set(x, z);
            worldToUi.transformPosition(result);
            uiPointTowardsEdge(result.x, result.y, worldEdgeBuffer * zoom / uiScale, result);
            uiToWorld.transformPosition(result);
        }

        @Override
        public boolean uiContains(double x, double z) {
            RectangleD bounds = this.uiBounds;
            return x >= bounds.x1
                    && z >= bounds.z1
                    && x <= bounds.x2
                    && z <= bounds.z2;
        }

        @Override
        public boolean uiContains(double x, double z, double uiRadius) {
            RectangleD bounds = this.uiBounds;
            return x >= bounds.x1 + uiRadius
                    && z >= bounds.z1 + uiRadius
                    && x <= bounds.x2 - uiRadius
                    && z <= bounds.z2 - uiRadius;
        }

        @Override
        public boolean uiIntersectsCircle(double x, double z, double uiRadius) {
            // Case 1: circle exists in a space perpendicular to one of the edge lines.
            RectangleD bounds = this.uiBounds;
            if ((x >= bounds.x1 && x <= bounds.x2 && z >= bounds.z1 - uiRadius && z <= bounds.z2 + uiRadius)
                    || (z >= bounds.z1 && z <= bounds.z2 && x >= bounds.x1 - uiRadius && x <= bounds.x2 + uiRadius)) {
                return true;
            }
            // Case 2: circle exists in one of the corner spaces
            double dx = (bounds.x1 >= x ? bounds.x1 : bounds.x2) - x;
            double dz = (bounds.z1 >= z ? bounds.z1 : bounds.z2) - z;
            return (dx * dx + dz * dz) <= uiRadius * uiRadius;
        }

        @Override
        public void uiPointTowardsEdge(double x, double z, double uiEdgeBuffer, Vector2d result) {
            RectangleD bounds = this.uiBounds;
            if (uiEdgeBuffer >= bounds.wdiv2 || uiEdgeBuffer >= bounds.hdiv2) {
                result.set(0, 0);
                return;
            }
            if (x == 0 && z == 0) {
                result.set(0, bounds.z1 + uiEdgeBuffer);
                return;
            }
            double sx = Math.abs((bounds.wdiv2 - uiEdgeBuffer) / x);
            double sz = Math.abs((bounds.hdiv2 - uiEdgeBuffer) / z);
            if (sx < sz) {
                result.set(x * sx, z * sx);
            } else {
                result.set(x * sz, z * sz);
            }
        }
    }
}
