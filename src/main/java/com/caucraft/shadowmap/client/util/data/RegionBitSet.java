package com.caucraft.shadowmap.client.util.data;

import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLongArray;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author caucow
 */
public class RegionBitSet {

    private transient ReentrantLock lock;
    private long[][][][] storage;

    public RegionBitSet() {
        this.lock = new ReentrantLock();
        // Actual size [4][256][256][1024]
        // Effective bit count [4][256][256][65536]
        storage = new long[4][][][];
    }

    /**
     * Not necessary to call for single writes as setRegion() sets the lock
     * before making any changes to the structure, but can be used to ensure
     * bulk writes happen atomically.
     *
     * No attempt is made to synchronize before reads.
     */
    public void lock() {
        lock.lock();
    }

    /**
     * Only necessary if lock() was explicitly called outside this object. See
     * docs for lock().
     */
    public void unlock() {
        lock.unlock();
    }

    public void setRegion(int rx, int rz, boolean val) {
        try {
            lock.lock();

            long k = getKey(rx, rz);
            int ik = (int) k;
            int e = ik & 0x3F;
            long v = val ? 1 : 0;
            v <<= e;

            // mask 0x0000_0003_0000_0000
            long[][][] a = storage[(int) (k >>> 32) & 0b0011];
            if (a == null) {
                a = storage[(int) (k >>> 32) & 0b0011] = new long[256][][];
            }
            // mask 0x0000_0000_FF00_0000
            long[][] b = a[ik >>> 24 & 0xFF];
            if (b == null) {
                b = a[ik >>> 24 & 0xFF] = new long[256][];
            }
            // mask 0x0000_0000_00FF_0000
            long[] c = b[ik >>> 16 & 0xFF];
            if (c == null) {
                c = b[ik >>> 16 & 0xFF] = new long[1024];
            }
            // mask 0x0000_0000_0000_FFC0
            long d = c[ik >>> 6 & 0x03FF];

            d = (d & ~v) | v;

            c[ik >>> 6 & 0x03FF] = d;
        } finally {
            lock.unlock();
        }
    }

    public boolean getRegion(int rx, int rz) {
        long k = getKey(rx, rz);
        int ik = (int) k;
        int e = ik & 0x3F;
        long v = 1L << e;

        // mask 0x0000_0003_0000_0000
        long[][][] a = storage[(int) (k >>> 32) & 0b0011];
        if (a == null) {
            return false;
        }
        // mask 0x0000_0000_FF00_0000
        long[][] b = a[ik >>> 24 & 0xFF];
        if (b == null) {
            return false;
        }
        // mask 0x0000_0000_00FF_0000
        long[] c = b[ik >>> 16 & 0xFF];
        if (c == null) {
            return false;
        }
        // mask 0x0000_0000_0000_FFC0
        long d = c[ik >>> 6 & 0x03FF];

        return (d & v) != 0;
    }

    private static long getKey(int regionX, int regionZ) {
        long longRegionX = regionX;
        long longRegionZ = regionZ;
        long value = (regionZ < 0 ? 0L : 0x0002_0000_0000L) | (regionX < 0 ? 0L : 0x0001_0000_0000L);
        value
                |= (longRegionZ & 0xF000) << 16 | (longRegionX & 0xF000) << 12
                | (longRegionZ & 0x0F00) << 12 | (longRegionX & 0x0F00) << 8
                | (longRegionZ & 0x00F0) << 8 | (longRegionX & 0x00F0) << 4
                | (longRegionZ & 0x000F) << 4 | (longRegionX & 0x000F);

        return value;
    }

    public NbtCompound toNbt() {
        return writeL0(storage);
    }

    public void loadNbt(NbtCompound root) throws IOException {
        readL0(root, storage);
    }

    /**
     * Combines the two sets as if by using a binary OR on all elements.
     */
    public void merge(RegionBitSet other) {
        try {
            lock.lock();
            long[][][][] mine = storage;
            long[][][][] theirs = other.storage;
            for (int i = 0; i < 4; i++) {
                if (mine[i] == null && theirs[i] != null) {
                    mine[i] = theirs[i];
                    continue;
                }
                if (mine[i] == null || theirs[i] == null) {
                    continue;
                }
                long[][][] myL0 = mine[i];
                long[][][] theirL0 = theirs[i];
                for (int j = 0; j < 256; j++) {
                    if (myL0[j] == null && theirL0[j] != null) {
                        myL0[j] = theirL0[j];
                        continue;
                    }
                    if (myL0[j] == null || theirL0[j] == null) {
                        continue;
                    }
                    long[][] myL1 = myL0[j];
                    long[][] theirL1 = theirL0[j];
                    for (int k = 0; k < 256; k++) {
                        if (myL1[k] == null && theirL1[k] != null) {
                            myL1[k] = theirL1[k];
                            continue;
                        }
                        if (myL1[k] == null || theirL1[k] == null) {
                            continue;
                        }
                        long[] myL2 = myL1[k];
                        long[] theirL2 = theirL1[k];
                        for (int l = 0; l < 1024; l++) {
                            myL2[k] |= theirL2[k];
                        }
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private static NbtCompound writeL0(long[][][][] array) {
        NbtCompound root = new NbtCompound();
        byte p = 0;
        NbtList dataArray = new NbtList();
        for (int i = 0; i < 4; i++) {
            if (array[i] != null) {
                p |= 1 << i;
                dataArray.add(writeL1(array[i]));
            }
        }
        root.put("p", NbtByte.of(p));
        root.put("0", dataArray);
        return root;

    }

    private static NbtCompound writeL1(long[][][] array) {
        NbtCompound root = new NbtCompound();
        long[] p = new long[4];
        NbtList dataArray = new NbtList();
        for (int i = 0; i < 256; i++) {
            if (array[i] != null) {
                p[i >> 6] |= 1L << (i & 0x3F);
                dataArray.add(writeL2(array[i]));
            }
        }
        root.put("p", new NbtLongArray(p));
        root.put("1", dataArray);
        return root;
    }

    private static NbtCompound writeL2(long[][] array) {
        NbtCompound root = new NbtCompound();
        long[] p = new long[4];
        NbtList dataArray = new NbtList();
        for (int i = 0; i < 256; i++) {
            if (array[i] != null) {
                p[i >> 6] |= 1L << (i & 0x3F);
                dataArray.add(new NbtLongArray(array[i]));
            }
        }
        root.put("p", new NbtLongArray(p));
        root.put("2", dataArray);
        return root;
    }

    private static void readL0(NbtCompound root, long[][][][] array) throws IOException {
        if (!root.contains("0", NbtElement.LIST_TYPE)) {
            throw new IOException("Data array missing");
        }
        if (!root.contains("p", NbtElement.BYTE_TYPE)) {
            throw new IOException("Presence flags missing");
        }

        byte p = root.getByte("p");
        NbtList dataArray = root.getList("0", NbtElement.COMPOUND_TYPE);
        for (int i = 0, di = 0; i < 4; i++) {
            if ((p & 1 << i) == 0) {
                array[i] = null;
            } else {
                if (array[i] == null) {
                    array[i] = new long[256][][];
                }
                readL1(dataArray.getCompound(di++), array[i]);
            }
        }
    }

    private static void readL1(NbtCompound root, long[][][] array) throws IOException {
        if (!root.contains("1", NbtElement.LIST_TYPE)) {
            throw new IOException("Data array missing");
        }
        if (!root.contains("p", NbtElement.LONG_ARRAY_TYPE)) {
            throw new IOException("Presence flags missing");
        }

        long[] p = root.getLongArray("p");
        NbtList dataArray = root.getList("1", NbtElement.COMPOUND_TYPE);
        if (p.length != 4) {
            throw new IOException("Presence flags length not 256");
        }

        for (int i = 0, di = 0; i < 256; i++) {
            if ((p[i >> 6] & 1L << (i & 0x3F)) == 0) {
                array[i] = null;
            } else {
                if (array[i] == null) {
                    array[i] = new long[256][];
                }
                readL2(dataArray.getCompound(di++), array[i]);
            }
        }
    }

    private static void readL2(NbtCompound root, long[][] array) throws IOException {
        if (!root.contains("2", NbtElement.LIST_TYPE)) {
            throw new IOException("Data array missing");
        }
        if (!root.contains("p", NbtElement.LONG_ARRAY_TYPE)) {
            throw new IOException("Presence flags missing");
        }

        long[] p = root.getLongArray("p");
        NbtList dataArray = root.getList("2", NbtElement.LONG_ARRAY_TYPE);
        if (p.length != 4) {
            throw new IOException("Presence flags length not 256");
        }

        for (int i = 0, di = 0; i < 256; i++) {
            if ((p[i >> 6] & 1L << (i & 0x3F)) == 0) {
                array[i] = null;
            } else {
                array[i] = ((NbtLongArray) dataArray.get(di++)).getLongArray();
            }
        }
    }
}
