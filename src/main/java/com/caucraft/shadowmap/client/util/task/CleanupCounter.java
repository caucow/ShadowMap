package com.caucraft.shadowmap.client.util.task;

public class CleanupCounter {
    private int blockMemory;
    private int metaMemory;
    private int highResTextures;
    private int lowResTextures;

    public int addBlockMemory(int regionUsage) {
        return blockMemory += regionUsage;
    }

    public int addMetaMemory(int regionUsage) {
        return metaMemory += regionUsage;
    }

    public int incHighResTextures() {
        return ++highResTextures;
    }

    public int incLowResTextures() {
        return ++ lowResTextures;
    }
}
