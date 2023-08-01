package com.caucraft.shadowmap.client.util.task;

public record CleanupHelper(int maxLoad, boolean blockLayer, boolean metaLayer, boolean highResTexture, boolean lowResTexture) {
    public boolean isAnyTrue() {
        return blockLayer | metaLayer | highResTexture | lowResTexture;
    }
}
