package com.caucraft.shadowmap.client.util.data;

import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtLongArray;

import java.util.Arrays;
import java.util.Objects;

public class CompactIntArray {
    private static final String BITS_PER_ELEMENT_KEY = "bpe";
    private static final String LENGTH_KEY = "len";
    private static final String DATA_KEY = "data";

    private final int bitsPerElement;
    public final int length;
    private final long elementMask;
    private long[] array;

    public CompactIntArray(int bitsPerElement, int length) {
        if (bitsPerElement < 1) {
            throw new IllegalArgumentException("Bits per element cannot be non-positive: " + bitsPerElement);
        }
        if (bitsPerElement > 32) {
            throw new IllegalArgumentException("Bits per element cannot be greater than 32: " + bitsPerElement);
        }
        this.bitsPerElement = bitsPerElement;
        this.length = length;
        this.elementMask = (1L << bitsPerElement) - 1L;
        int bitsTotal = length * bitsPerElement;
        this.array = new long[(bitsTotal >>> 6) + ((bitsTotal & 0x3F) != 0 ? 1 : 0)];
    }

    private void boundsCheck(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("External index is out of bounds of compact size: " + index);
        }
    }

    /**
     * Gets a value from the compacted long storage. Non-power-of-two
     * bitsPerElement may require merging value bits from multiple longs, in
     * which case the low bits of the value are taken from the high bits of the
     * long at the lower index, and the high bits of the value are taken from
     * the low bits of the long at the higher index.
     * @param index external index of the value in storage
     * @return the value at the provided external index. Negative values must be
     * interpreted outside the compact array.
     */
    public int get(int index) {
        boundsCheck(index);
        long[] local = array;
        index = index * bitsPerElement; // Bit index across all compacted longs
        int longIndex = index >>> 6;
        int longBitOffset = index & 0x3F;
        long value = local[longIndex] >>> longBitOffset & elementMask;
        int bitsPresent = 64 - longBitOffset;
        if (bitsPresent < bitsPerElement) {
            value |= (local[longIndex + 1] & elementMask >>> bitsPresent) << bitsPresent;
        }
        return (int) value;
    }

    /**
     * Gets multiple values from the compacted long storage, placing them in the
     * provided destination array.
     * @param startIndex the first index to copy to the destination array
     * @param endIndex the index to stop copying to the destination array
     * @param dest the destination array
     * @param destStart the start position in the destination array
     */
    public void get(int startIndex, int endIndex, int[] dest, int destStart) {
        boundsCheck(startIndex);
        boundsCheck(endIndex - 1);
        long[] local = array;
        int bpe = bitsPerElement;
        long mask = elementMask;
        int bitIndex = startIndex * bpe;
        int longIndex = bitIndex >>> 6;
        int longBitOffset = bitIndex & 0x3F;
        long compact = local[longIndex] >>> longBitOffset;
        int bitsPresent = 64 - longBitOffset;
        for (int i = startIndex, d = destStart; i < endIndex; i++, d++) {
            long value = compact & mask;
            if (bitsPresent < bpe) {
                longIndex++;
                compact = local[longIndex];
                value |= (compact & mask >>> bitsPresent) << bitsPresent;
                compact >>>= bpe - bitsPresent;
                bitsPresent += 64;
            } else {
                compact >>>= bpe;
            }
            bitsPresent -= bpe;
            dest[d] = (int) value;
        }
    }

    /**
     * Sets a value in the compacted long storage. Non-power-of-two
     * bitsPerElement may require splitting value bits across multiple longs, in
     * which case the low bits of the value are placed in the high bits of the
     * long at the lower index, and the high bits of the value are placed in the
     * low bits of the long at the higher index. Masking is used to ensure the
     * value is within the range allowed by bitsPerElement.
     * @param index external index of the value in storage
     * @param newValue the value to set at the provided external index. Negative
     * values must be interpreted outside the compact array.
     */
    public void set(int index, int newValue) {
        boundsCheck(index);
        long[] local = array;
        index = index * bitsPerElement; // Bit index across all compacted longs
        int valueIndex = index >>> 6;
        int valueBitOffset = index & 0x3F;
            /* What it's doing:
            long valueSelectMask = elementMask << valueBitOffset;
            long value = local[valueIndex];
            value = value & ~valueSelectMask | (long) newValue << valueBitOffset;
            local[valueIndex] = value;
             */
        local[valueIndex] = local[valueIndex]
                & ~(((1L << bitsPerElement) - 1L) << valueBitOffset)
                | ((long) newValue & elementMask) << valueBitOffset;
        int bitsPresent = 64 - valueBitOffset;
        if (bitsPresent < bitsPerElement) {
                /* What it's doing:
                valueSelectMask = elementMask >>> bitsPresent;
                value = local[valueIndex + 1];
                value = value & ~valueSelectMask | newValue >>> bitsPresent;
                local[valueIndex + 1] = value;
                 */
            local[valueIndex + 1] = local[valueIndex + 1]
                    & ~(elementMask >>> bitsPresent)
                    | ((long) newValue & elementMask) >>> bitsPresent;
        }
    }

    public int estimateMemoryUsage() {
        return 40 + 8 * array.length;
    }

    public NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();
        root.put(BITS_PER_ELEMENT_KEY, NbtByte.of((byte) bitsPerElement));
        root.put(LENGTH_KEY, NbtInt.of(length));
        root.put(DATA_KEY, new NbtLongArray(array));
        return root;
    }

    public void loadNbt(NbtCompound root) {
        int bitsPerElementNbt = root.getByte(BITS_PER_ELEMENT_KEY);
        int lengthNbt = root.getInt(LENGTH_KEY);
        long[] arrayNbt = root.getLongArray(DATA_KEY);
        if (bitsPerElement != bitsPerElementNbt) {
            throw new IllegalArgumentException("Passed compound has a different bitsPerElement: " + bitsPerElementNbt + " != " + bitsPerElement);
        }
        if (length != lengthNbt) {
            throw new IllegalArgumentException("Passed compound has a different length: " + lengthNbt + " != " + length);
        }
        if (array.length != arrayNbt.length) {
            throw new IllegalArgumentException("Passed compound has a different data length: " + arrayNbt.length + " != " + array.length);
        }
        array = arrayNbt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        CompactIntArray that = (CompactIntArray) o;
        return bitsPerElement == that.bitsPerElement && length == that.length && Arrays.equals(array, that.array);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(bitsPerElement, length, elementMask);
        result = 31 * result + Arrays.hashCode(array);
        return result;
    }
}
