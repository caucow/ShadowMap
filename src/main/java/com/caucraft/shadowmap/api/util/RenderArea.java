package com.caucraft.shadowmap.api.util;

import java.util.Objects;

public class RenderArea {

    public static final RenderArea EMPTY_AREA = new RenderArea(0, 0, 0, 0) {
        @Override public boolean containsRegion(int regionX, int regionZ) { return false; }
        @Override public long getRenderPriority(int regionX, int regionZ) { return Long.MAX_VALUE >>> 1; }
    };
    public static final long FLAG_OUTSIDE_AREA = 0x4000_0000_0000_0000L;

    private final int minRegionX;
    private final int minRegionZ;
    private final int maxRegionX;
    private final int maxRegionZ;

    public RenderArea(int minRegionX, int minRegionZ, int maxRegionX, int maxRegionZ) {
        this.minRegionX = minRegionX;
        this.minRegionZ = minRegionZ;
        this.maxRegionX = maxRegionX;
        this.maxRegionZ = maxRegionZ;
    }

    public int minX() {
        return minRegionX;
    }

    public int minZ() {
        return minRegionZ;
    }

    public int maxX() {
        return maxRegionX;
    }

    public int maxZ() {
        return maxRegionZ;
    }

    public int centerX() {
        return (minRegionX + maxRegionX) / 2;
    }

    public int centerZ() {
        return (minRegionZ + maxRegionZ) / 2;
    }

    public int width() {
        return maxRegionX - minRegionX + 1;
    }

    public int height() {
        return maxRegionZ - minRegionZ + 1;
    }

    public boolean containsRegion(int regionX, int regionZ) {
        return regionX >= minRegionX && regionZ >= minRegionZ && regionX <= maxRegionX && regionZ <= maxRegionZ;
    }

    public boolean containsChunk(int chunkX, int chunkZ) {
        return containsRegion(chunkX >> 5, chunkZ >> 5);
    }

    public boolean containsBlock(int blockX, int blockZ) {
        return containsRegion(blockX >> 9, blockZ >> 9);
    }

    public long getRenderPriority(int regionX, int regionZ) {
        int centerX = (minRegionX + maxRegionX) >> 1;
        int centerZ = (minRegionZ + maxRegionZ) >> 1;
        long diffX = centerX - regionX;
        long diffZ = centerZ - regionZ;
        long distancePriority = diffX * diffX + diffZ * diffZ;
        if (regionX < minRegionX | regionX > maxRegionX | regionZ < minRegionZ | regionZ > maxRegionZ) {
            distancePriority |= FLAG_OUTSIDE_AREA;
        }
        return distancePriority;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (RenderArea) obj;
        return this.minRegionX == that.minRegionX && this.minRegionZ == that.minRegionZ
                && this.maxRegionX == that.maxRegionX && this.maxRegionZ == that.maxRegionZ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minRegionX, minRegionZ, maxRegionX, maxRegionZ);
    }

    @Override
    public String toString() {
        return "RenderArea[" + minRegionX + ", " + minRegionZ + " -> " + maxRegionX + ", " + maxRegionZ + ']';
    }

    public static long getRenderPriority(RenderArea[] priorityArray, int regionX, int regionZ) {
        long priority = Long.MAX_VALUE;
        for (int i = priorityArray.length - 1; i >= 0; i--) {
            RenderArea priorityArea = priorityArray[i];
            priority = Math.min(priority, priorityArea.getRenderPriority(regionX, regionZ));
        }
        return priority;
    }
}
