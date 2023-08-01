package com.caucraft.shadowmap.client.util.data;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.api.util.IntToObjectFunction;
import com.caucraft.shadowmap.client.util.MapUtils;
import com.caucraft.shadowmap.api.util.ObjectToIntFunction;
import com.caucraft.shadowmap.client.util.TriFunction;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.nbt.NbtShort;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.MathHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Objects;

/**
 * A paletted storage container similar to that used by Minecraft's chunk
 * storage, but rewritten, maybe worse, specifically for this map. This acts as
 * a fixed-size array storage, with a palette and an internal array where each
 * bit-compacted element is a pointer to an index in the palette. This container
 * will be thread-safe to read from, but external locking should be used when
 * writing. Also prematurely micro-optimized to hell, reader beware.
 * @param <T> type of object contained in the palette.
 */
public class PaletteStorage<T> {

    private static final String
            STORAGE_SIZE_KEY = "s",
            STORAGE_TYPE_KEY = "t",
            PALETTE_CAPACITY_KEY = "plc",
            PALETTE_SIZE_KEY = "pls",
            PALETTE_KEY = "pl",
            POINTERS_BITS_PER_ENTRY_KEY = "ptb",
            POINTERS_KEY = "pt";

    private int storageSize;
    private final Class<?> contentType;
    private InternalStorage<T> storage;
    private int changes;

    public PaletteStorage(int storageSize, Class<?> contentType) {
        this.storageSize = storageSize;
        this.contentType = contentType;
    }

    public int size() {
        return storageSize;
    }

    public int getPaletteSize() {
        return storage.getPaletteSize();
    }

    public int estimateMemoryUsage() {
        return 32 + (storage == null ? 0 : storage.estimateMemoryUsage());
    }

    public void ensurePaletteCapacity(int capacity) {
        if (capacity <= 2 || storage != null && storage.getPaletteCapacity() >= capacity) {
            return;
        }
        if (capacity <= 4) {
            storage = new QuadPaletteStorage<>(storageSize, null);
            changes++;
        } else if (capacity <= 8) {
            storage = new OctoPaletteStorage<>(storageSize, null);
            changes++;
        } else if (MathHelper.ceilLog2(capacity) < MathHelper.ceilLog2(storageSize)) {
            storage = new ArrayPaletteStorage<>(contentType, MathHelper.ceilLog2(capacity), storageSize, null);
            changes++;
        } else {
            storage = new ArrayRawStorage<>(contentType, storageSize);
            changes++;
        }
    }

    public void set(int index, T value) {
        boundsCheck(index);
        if (storage == null) {
            storage = new SinglePaletteStorage<>(value);
            changes++;
            return;
        }
        if (storage.set(index, value)) {
            changes++;
            return;
        }
        storage = storage.compact(contentType, storageSize, false);
        changes++;
        if (storage.getPaletteSize() == storage.getPaletteCapacity()) {
            storage = storage.createLargerWith(contentType, storageSize, value);
            changes++;
        }
        if (storage.set(index, value)) {
            changes++;
            return;
        }
        throw new IllegalStateException("Could not add to storage palette after increasing size");
    }

    public T get(int index) {
        boundsCheck(index);
        InternalStorage<T> localStorage = storage;
        if (localStorage == null) {
            return null;
        }
        return localStorage.get(index);
    }

    public void compact() {
        this.storage = storage.compact(contentType, storageSize, true);
        changes++;
    }

    private void boundsCheck(int index) {
        if (index < 0 || index >= storageSize) {
            throw new IndexOutOfBoundsException("Storage index is out of bounds of storage size: " + index);
        }
    }

    public Iterator<T> iterator(int startIndex) {
        InternalStorage<T> localStorage = storage;
        if (localStorage == null) {
            return new LengthLimitedIterator<>(new Iterator<T>() {
                @Override public boolean hasNext() { return true; }
                @Override public T next() { return null; }
            });
        }
        return new LengthLimitedIterator<>(localStorage.iterator(startIndex));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PaletteStorage<?> that = (PaletteStorage<?>) o;
        return storageSize == that.storageSize && Objects.equals(contentType, that.contentType) && Objects.equals(storage,
                that.storage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storageSize, contentType, storage);
    }

    public void loadNbt(NbtCompound root, IntToObjectFunction<T> paletteIndexer) throws IOException {
        if (!root.contains(STORAGE_TYPE_KEY, NbtElement.STRING_TYPE)) {
            return;
        }
        StorageType type;
        try {
            type = StorageType.valueOf(root.getString(STORAGE_TYPE_KEY));
        } catch (IllegalArgumentException ex) {
            ShadowMap.getLogger().warn("Invalid palette storage type: " + root.getString(STORAGE_TYPE_KEY));
            return;
        }
        storageSize = root.getInt(STORAGE_SIZE_KEY);
        storage = (InternalStorage<T>) type.storageDeserializer.apply(this.contentType, root, paletteIndexer);
        changes++;
    }

    public NbtCompound toNbt(ObjectToIntFunction<T> paletteIndexer) {
        if (storage == null) {
            NbtCompound root = new NbtCompound();
            root.put(STORAGE_SIZE_KEY, NbtInt.of(storageSize));
            return root;
        }
        NbtCompound root = storage.toNbt(paletteIndexer);
        root.put(STORAGE_SIZE_KEY, NbtInt.of(storageSize));
        root.put(STORAGE_TYPE_KEY, NbtString.of(storage.getStorageType().name()));
        return root;
    }

    private interface InternalStorage<T> {
        /**
         * Attempts to set the element at the specified index to the provided
         * value.
         * @param index the external index of the value
         * @param value the value to set
         * @return true if the value at the given index could be set, false if
         * the palette needs to be expanded to accommodate the value.
         */
        boolean set(int index, T value);

        /**
         * @param index the external index of the value
         * @return the value at the provided index
         */
        T get(int index);

        /**
         * @return the current size of the palette for this storage
         */
        int getPaletteSize();

        /**
         * @return the capacity of the current palette
         */
        int getPaletteCapacity();

        StorageType getStorageType();

        int estimateMemoryUsage();

        /**
         * Creates an internal storage object with higher bitsPerEntry and adds
         * the provided palette entry to the enlarged palette.
         *
         * @param contentType the type of T, the content in storage
         * @param addedPaletteEntry palette entry to add to the enlarged palette
         * @return an internal storage object with larger palette capacity
         */
        InternalStorage<T> createLargerWith(Class<?> contentType, int length, T addedPaletteEntry);

        Iterator<T> iterator(int startIndex);

        NbtCompound toNbt(ObjectToIntFunction<T> paletteIndexer);

        default InternalStorage<T> compact(Class<?> contentType, int length, boolean force) {
            if (getPaletteSize() < 8 && !force) {
                return this;
            }
            ReferenceOpenHashSet<T> isPresent = new ReferenceOpenHashSet<>(getPaletteSize());
            for (int i = 0; i < length; i++) {
                isPresent.add(get(i));
            }
            if (getPaletteSize() == isPresent.size()) {
                return this;
            }
            if (isPresent.isEmpty()) {
                return null;
            }
            if (isPresent.size() == 1) {
                return new SinglePaletteStorage<>(isPresent.iterator().next());
            }
            InternalStorage<T> newStorage;
            if (isPresent.size() == 2) {
                Iterator<T> keyIterator = isPresent.iterator();
                newStorage = new DoublePaletteStorage<>(length, keyIterator.next(), keyIterator.next());
            } else if (isPresent.size() <= 4) {
                newStorage = new QuadPaletteStorage<>(length, null);
            } else if (isPresent.size() <= 8) {
                newStorage = new OctoPaletteStorage<>(length, null);
            } else if (MathHelper.ceilLog2(isPresent.size()) < MathHelper.ceilLog2(length)) {
                newStorage = new ArrayPaletteStorage<>(contentType, MathHelper.ceilLog2(isPresent.size()), length, null);
            } else {
                newStorage = new ArrayRawStorage<>(contentType, length);
            }
            for (int i = 0; i < length; i++) {
                if (!newStorage.set(i, get(i))) {
                    throw new IllegalStateException("Resized storage could not fit known palette size");
                }
            }
            return newStorage;
        }
    }

    private static final class SinglePaletteStorage<T> implements InternalStorage<T> {
        private final T value;

        SinglePaletteStorage(Class<?> contentType, NbtCompound root, IntToObjectFunction<T> paletteIndexer) {
            this.value = paletteIndexer.apply(root.getInt(PALETTE_KEY));
        }

        SinglePaletteStorage(T value) {
            this.value = value;
        }

        @Override
        public boolean set(int index, T value) {
            return value == this.value;
        }

        @Override
        public T get(int index) {
            return value;
        }

        @Override
        public int getPaletteSize() {
            return 1;
        }

        @Override
        public int getPaletteCapacity() {
            return 1;
        }

        @Override
        public StorageType getStorageType() {
            return StorageType.SINGLE;
        }

        @Override
        public int estimateMemoryUsage() {
            return 20;
        }

        @Override
        public NbtCompound toNbt(ObjectToIntFunction<T> paletteIndexer) {
            NbtCompound root = new NbtCompound();
            root.put(PALETTE_KEY, NbtInt.of(paletteIndexer.apply(value)));
            return root;
        }

        @Override
        public InternalStorage<T> createLargerWith(Class<?> contentType, int length, T addedPaletteEntry) {
            return new DoublePaletteStorage<>(length, value, addedPaletteEntry);
        }

        public Iterator<T> iterator(int startIndex) {
            return new Iterator<T>() {
                @Override
                public boolean hasNext() {
                    return true;
                }

                @Override
                public T next() {
                    return value;
                }
            };
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SinglePaletteStorage<?> that = (SinglePaletteStorage<?>) o;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    private static abstract class CompactLongStorage<T> implements InternalStorage<T> {
        protected final long[] contentPointers;

        protected CompactLongStorage(NbtCompound root) {
            contentPointers = root.getLongArray(POINTERS_KEY);
        }

        protected CompactLongStorage(int bitsPerEntry, int length) {
            int bitsTotal = length * bitsPerEntry;
            this.contentPointers = new long[(bitsTotal >>> 6) + ((bitsTotal & 0x3F) != 0 ? 1 : 0)];
        }

        /**
         * Gets a palette pointer from the compacted long storage.
         * Non-power-of-two bitsPerEntry may require merging pointer bits from
         * multiple longs, in which case the low bits of the pointer are taken
         * from the high bits of the long at the lower index, and the high bits
         * of the pointer are taken from the low bits of the long at the higher
         * index. (What a brain cell twister that mess is!)
         * @param bitsPerEntry the number of bits per pointer in the storage
         * @param index external index of the pointer in storage
         * @return the pointer at the provided external index
         */
        protected int getPointerAt(int bitsPerEntry, int index) {
            index = index * bitsPerEntry; // Bit index across all compacted longs
            int pointerIndex = index >>> 6;
            int pointerBitOffset = index & 0x3F;
            long pointer = contentPointers[pointerIndex] >>> pointerBitOffset & ~(-1L << bitsPerEntry);
            int bitsPresent = 64 - pointerBitOffset;
            if (bitsPresent < bitsPerEntry) {
                pointer |= (contentPointers[pointerIndex + 1] & ~(-1L << (bitsPerEntry - bitsPresent))) << bitsPresent;
            }
            return (int) pointer;
        }

        /**
         * Sets a palette pointer in the compacted long storage.
         * Non-power-of-two bitsPerEntry may require splitting pointer bits
         * across multiple longs, in which case the low bits of the pointer are
         * placed in the high bits of the long at the lower index, and the high
         * bits of the pointer are placed in the low bits of the long at the
         * higher index. No masking is done on the new pointer, it is expected
         * the pointer be within the range of values allowed by bitsPerEntry.
         * @param bitsPerEntry the number of bits per pointer in the storage
         * @param index external index of the pointer in storage
         * @param newPointer the pointer to set at the provided external index
         */
        protected void setPointerAt(int bitsPerEntry, int index, int newPointer) {
            index = index * bitsPerEntry; // Bit index across all compacted longs
            int pointerIndex = index >>> 6;
            int pointerBitOffset = index & 0x3F;
            /* What it's doing:
            long pointerSelectMask = ((1L << bitsPerEntry) - 1L) << pointerBitOffset;
            long pointer = contentPointers[pointerIndex];
            pointer = pointer & ~pointerSelectMask | (long) newPointer << pointerBitOffset;
            contentPointers[pointerIndex] = pointer;
             */
            contentPointers[pointerIndex] = contentPointers[pointerIndex]
                    & ~(((1L << bitsPerEntry) - 1L) << pointerBitOffset)
                    | (long) newPointer << pointerBitOffset;
            int bitsPresent = 64 - pointerBitOffset;
            if (bitsPresent < bitsPerEntry) {
                /* What it's doing:
                pointerSelectMask = ((1L << bitsPerEntry) - 1L) >>> bitsPresent;
                pointer = contentPointers[pointerIndex + 1];
                pointer = pointer & ~pointerSelectMask | newPointer >>> bitsPresent;
                contentPointers[pointerIndex + 1] = pointer;
                 */
                contentPointers[pointerIndex + 1] = contentPointers[pointerIndex + 1]
                        & ~(((1L << bitsPerEntry) - 1L) >>> bitsPresent)
                        | newPointer >>> bitsPresent;
            }
        }

        protected CompactLongStorage<T> withPointers(int oldbitsPerEntry, int newbitsPerEntry,
                int contentLength, CompactLongStorage<T> oldStorage) {
            for (int i = 0; i < contentLength; i++) {
                setPointerAt(newbitsPerEntry, i, oldStorage.getPointerAt(oldbitsPerEntry, i));
            }
            return this;
        }

        protected abstract T getPaletteEntry(int pointer);

        @Override
        public int estimateMemoryUsage() {
            return 24 + 8 * contentPointers.length;
        }

        @Override
        public NbtCompound toNbt(ObjectToIntFunction<T> paletteIndexer) {
            NbtCompound root = new NbtCompound();
            root.put(POINTERS_KEY, new NbtLongArray(contentPointers));
            return root;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CompactLongStorage<?> that = (CompactLongStorage<?>) o;
            return Arrays.equals(contentPointers, that.contentPointers);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(contentPointers);
        }

        class CompactLongIterator implements Iterator<T> {
            private final long mask;
            private final int bitsPerEntry;
            private int bitsPresent;
            private int longIndex;
            private long compact;

            CompactLongIterator(int startIndex, int bitsPerEntry) {
                this.mask = (1L << bitsPerEntry) - 1L;
                this.bitsPerEntry = bitsPerEntry;
                int bitIndex = startIndex * bitsPerEntry;
                this.longIndex = bitIndex >>> 6;
                int longBitOffset = bitIndex & 0x3F;
                this.compact = contentPointers[longIndex] >>> longBitOffset;
                this.bitsPresent = 64 - longBitOffset;
            }

            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public T next() {
                long compact = this.compact;
                long mask = this.mask;
                int bitsPerEntry = this.bitsPerEntry;
                int bitsPresent = this.bitsPresent;
                long pointer = compact & mask;
                if (bitsPresent < bitsPerEntry) {
                    compact = contentPointers[++this.longIndex];
                    pointer |= (compact & mask >>> bitsPresent) << bitsPresent;
                    compact >>>= bitsPerEntry - bitsPresent;
                    bitsPresent += 64;
                } else {
                    compact >>>= bitsPerEntry;
                }
                bitsPresent -= bitsPerEntry;
                this.bitsPresent = bitsPresent;
                this.compact = compact;
                return getPaletteEntry((int) pointer);
            }
        }
    }

    private static class DoublePaletteStorage<T> extends CompactLongStorage<T> {
        private final T a, b;

        DoublePaletteStorage(Class<?> contentType, NbtCompound root, IntToObjectFunction<T> paletteIndexer) {
            super(root);
            int[] palette = root.getIntArray(PALETTE_KEY);
            this.a = paletteIndexer.apply(palette[0]);
            this.b = paletteIndexer.apply(palette[1]);
        }

        DoublePaletteStorage(int length, T a, T b) {
            super(1, length);
            this.a = a;
            this.b = b;
        }

        @Override
        public boolean set(int index, T value) {
            int pointer;
            if (value == a) {
                pointer = 0;
            } else if (value == b) {
                pointer = 1;
            } else {
                return false;
            }
            setPointerAt(1, index, pointer);
            return true;
        }

        @Override
        public T get(int index) {
            return switch (getPointerAt(1, index)) {
                case 0 -> a;
                case 1 -> b;
                default -> throw new IllegalStateException(
                        "Pointer value at index " + index + " is outside the valid range for "
                                + getClass().getSimpleName());
            };
        }

        @Override
        public int getPaletteSize() {
            return 2;
        }

        @Override
        public int getPaletteCapacity() {
            return 2;
        }

        @Override
        public StorageType getStorageType() {
            return StorageType.DOUBLE;
        }

        @Override
        public int estimateMemoryUsage() {
            return super.estimateMemoryUsage() + 16;
        }

        @Override
        public InternalStorage<T> createLargerWith(Class<?> contentType, int length, T addedPaletteEntry) {
            T[] content = (T[]) Array.newInstance(contentType, 3);
            content[0] = a;
            content[1] = b;
            content[2] = addedPaletteEntry;
            return new QuadPaletteStorage<>(length, content).withPointers(1, 2, length, this);
        }

        @Override
        protected T getPaletteEntry(int pointer) {
            return switch (pointer) {
                case 0 -> a;
                case 1 -> b;
                default -> throw new IllegalStateException(
                        "Pointer value " + pointer + " is outside the valid range for "
                                + getClass().getSimpleName());
            };
        }

        @Override
        public NbtCompound toNbt(ObjectToIntFunction<T> paletteIndexer) {
            NbtCompound root = super.toNbt(paletteIndexer);
            root.put(PALETTE_KEY, new NbtIntArray(new int[] {
                    paletteIndexer.apply(a),
                    paletteIndexer.apply(b)
            }));
            return root;
        }

        @Override
        public Iterator<T> iterator(int startIndex) {
            return new CompactLongIterator(startIndex, 1);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            DoublePaletteStorage<?> that = (DoublePaletteStorage<?>) o;
            return Objects.equals(a, that.a) && Objects.equals(b, that.b);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), a, b);
        }
    }

    private static class QuadPaletteStorage<T> extends CompactLongStorage<T> {
        private int paletteSize;
        private T a, b, c, d;

        QuadPaletteStorage(Class<?> contentType, NbtCompound root, IntToObjectFunction<T> paletteIndexer) {
            super(root);
            this.paletteSize = root.getByte(PALETTE_SIZE_KEY);
            int[] palette = root.getIntArray(PALETTE_KEY);
            this.a = paletteIndexer.apply(palette[0]);
            this.b = paletteIndexer.apply(palette[1]);
            this.c = paletteIndexer.apply(palette[2]);
            this.d = paletteIndexer.apply(palette[3]);
        }

        QuadPaletteStorage(int length, T[] paletteContent) {
            super(2, length);
            this.paletteSize = paletteContent == null ? 0 : paletteContent.length;
            if (paletteSize > 0) {
                this.a = paletteContent[0];
            }
            if (paletteSize > 1) {
                this.b = paletteContent[1];
            }
            if (paletteSize > 2) {
                this.c = paletteContent[2];
            }
            if (paletteSize > 3) {
                this.d = paletteContent[3];
            }
        }

        @Override
        public boolean set(int index, T value) {
            int pointer;
            if (paletteSize > 0 && value == a) {
                pointer = 0;
            } else if (paletteSize > 1 && value == b) {
                pointer = 1;
            } else if (paletteSize > 2 && value == c) {
                pointer = 2;
            } else if (paletteSize > 3 && value == d) {
                pointer = 3;
            } else if (paletteSize < 4) {
                pointer = paletteSize;
                paletteSize++;
                switch (pointer) {
                    case 0 -> a = value;
                    case 1 -> b = value;
                    case 2 -> c = value;
                    case 3 -> d = value;
                }
            } else {
                return false;
            }
            setPointerAt(2, index, pointer);
            return true;
        }

        @Override
        public T get(int index) {
            return switch (getPointerAt(2, index)) {
                case 0 -> a;
                case 1 -> b;
                case 2 -> c;
                case 3 -> d;
                default -> throw new IllegalStateException(
                        "Pointer value at index " + index + " is outside the valid range for "
                                + getClass().getSimpleName());
            };
        }

        @Override
        public int getPaletteSize() {
            return paletteSize;
        }

        @Override
        public int getPaletteCapacity() {
            return 4;
        }

        @Override
        public StorageType getStorageType() {
            return StorageType.QUAD;
        }

        @Override
        public int estimateMemoryUsage() {
            return super.estimateMemoryUsage() + 36;
        }

        @Override
        public InternalStorage<T> createLargerWith(Class<?> contentType, int length, T addedPaletteEntry) {
            T[] content = (T[]) Array.newInstance(contentType, 5);
            content[0] = a;
            content[1] = b;
            content[2] = c;
            content[3] = d;
            content[4] = addedPaletteEntry;
            return new OctoPaletteStorage<>(length, content).withPointers(2, 3, length, this);
        }

        @Override
        protected T getPaletteEntry(int pointer) {
            return switch (pointer) {
                case 0 -> a;
                case 1 -> b;
                case 2 -> c;
                case 3 -> d;
                default -> throw new IllegalStateException(
                        "Pointer value " + pointer + " is outside the valid range for "
                                + getClass().getSimpleName());
            };
        }

        @Override
        public NbtCompound toNbt(ObjectToIntFunction<T> paletteIndexer) {
            NbtCompound root = super.toNbt(paletteIndexer);
            root.put(PALETTE_SIZE_KEY, NbtByte.of((byte) paletteSize));
            root.put(PALETTE_KEY, new NbtIntArray(new int[] {
                    paletteIndexer.apply(a),
                    paletteIndexer.apply(b),
                    paletteIndexer.apply(c),
                    paletteIndexer.apply(d)
            }));
            return root;
        }

        @Override
        public Iterator<T> iterator(int startIndex) {
            return new CompactLongIterator(startIndex, 2);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            QuadPaletteStorage<?> that = (QuadPaletteStorage<?>) o;
            return paletteSize == that.paletteSize && Objects.equals(a, that.a) && Objects.equals(b, that.b)
                    && Objects.equals(c, that.c) && Objects.equals(d, that.d);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), paletteSize, a, b, c, d);
        }
    }

    // Over the top? Maybe a little. Kinda gross? No, very.
    private static class OctoPaletteStorage<T> extends CompactLongStorage<T> {
        private int paletteSize;
        private T a, b, c, d, e, f, g, h;

        OctoPaletteStorage(Class<?> contentType, NbtCompound root, IntToObjectFunction<T> paletteIndexer) {
            super(root);
            this.paletteSize = root.getByte(PALETTE_SIZE_KEY);
            int[] palette = root.getIntArray(PALETTE_KEY);
            this.a = paletteIndexer.apply(palette[0]);
            this.b = paletteIndexer.apply(palette[1]);
            this.c = paletteIndexer.apply(palette[2]);
            this.d = paletteIndexer.apply(palette[3]);
            this.e = paletteIndexer.apply(palette[4]);
            this.f = paletteIndexer.apply(palette[5]);
            this.g = paletteIndexer.apply(palette[6]);
            this.h = paletteIndexer.apply(palette[7]);
        }

        OctoPaletteStorage(int length, T[] paletteContent) {
            super(3, length);
            this.paletteSize = paletteContent == null ? 0 : paletteContent.length;
            if (paletteSize > 0) {
                this.a = paletteContent[0];
            }
            if (paletteSize > 1) {
                this.b = paletteContent[1];
            }
            if (paletteSize > 2) {
                this.c = paletteContent[2];
            }
            if (paletteSize > 3) {
                this.d = paletteContent[3];
            }
            if (paletteSize > 4) {
                this.e = paletteContent[4];
            }
            if (paletteSize > 5) {
                this.f = paletteContent[5];
            }
            if (paletteSize > 6) {
                this.g = paletteContent[6];
            }
            if (paletteSize > 7) {
                this.h = paletteContent[7];
            }
        }

        @Override
        public boolean set(int index, T value) {
            int pointer;
            if (paletteSize > 0 && value == a) {
                pointer = 0;
            } else if (paletteSize > 1 && value == b) {
                pointer = 1;
            } else if (paletteSize > 2 && value == c) {
                pointer = 2;
            } else if (paletteSize > 3 && value == d) {
                pointer = 3;
            } else if (paletteSize > 4 && value == e) {
                pointer = 4;
            } else if (paletteSize > 5 && value == f) {
                pointer = 5;
            } else if (paletteSize > 6 && value == g) {
                pointer = 6;
            } else if (paletteSize > 7 && value == h) {
                pointer = 7;
            } else if (paletteSize < 8) {
                pointer = paletteSize;
                paletteSize++;
                switch (pointer) {
                    case 0 -> a = value;
                    case 1 -> b = value;
                    case 2 -> c = value;
                    case 3 -> d = value;
                    case 4 -> e = value;
                    case 5 -> f = value;
                    case 6 -> g = value;
                    case 7 -> h = value;
                }
            } else {
                return false;
            }
            setPointerAt(3, index, pointer);
            return true;
        }

        @Override
        public T get(int index) {
            return switch (getPointerAt(3, index)) {
                case 0 -> a;
                case 1 -> b;
                case 2 -> c;
                case 3 -> d;
                case 4 -> e;
                case 5 -> f;
                case 6 -> g;
                case 7 -> h;
                default -> throw new IllegalStateException(
                        "Pointer value at index " + index + " is outside the valid range for "
                                + getClass().getSimpleName());
            };
        }

        @Override
        public int getPaletteSize() {
            return paletteSize;
        }

        @Override
        public int getPaletteCapacity() {
            return 8;
        }

        @Override
        public StorageType getStorageType() {
            return StorageType.OCTO;
        }

        @Override
        public int estimateMemoryUsage() {
            return super.estimateMemoryUsage() + 68;
        }

        @Override
        public InternalStorage<T> createLargerWith(Class<?> contentType, int length, T addedPaletteEntry) {
            T[] content = (T[]) Array.newInstance(contentType, 9);
            content[0] = a;
            content[1] = b;
            content[2] = c;
            content[3] = d;
            content[4] = e;
            content[5] = f;
            content[6] = g;
            content[7] = h;
            content[8] = addedPaletteEntry;
            return new ArrayPaletteStorage<>(contentType, 4, length, content).withPointers(3, 4, length, this);
        }

        @Override
        public NbtCompound toNbt(ObjectToIntFunction<T> paletteIndexer) {
            NbtCompound root = super.toNbt(paletteIndexer);
            root.put(PALETTE_SIZE_KEY, NbtByte.of((byte) paletteSize));
            root.put(PALETTE_KEY, new NbtIntArray(new int[] {
                    paletteIndexer.apply(a),
                    paletteIndexer.apply(b),
                    paletteIndexer.apply(c),
                    paletteIndexer.apply(d),
                    paletteIndexer.apply(e),
                    paletteIndexer.apply(f),
                    paletteIndexer.apply(g),
                    paletteIndexer.apply(h)
            }));
            return root;
        }

        @Override
        public Iterator<T> iterator(int startIndex) {
            return new CompactLongIterator(startIndex, 3);
        }

        @Override
        protected T getPaletteEntry(int pointer) {
            return switch (pointer) {
                case 0 -> a;
                case 1 -> b;
                case 2 -> c;
                case 3 -> d;
                case 4 -> e;
                case 5 -> f;
                case 6 -> g;
                case 7 -> h;
                default -> throw new IllegalStateException(
                        "Pointer value " + pointer + " is outside the valid range for "
                                + getClass().getSimpleName());
            };
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            OctoPaletteStorage<?> that = (OctoPaletteStorage<?>) o;
            return paletteSize == that.paletteSize && Objects.equals(a, that.a) && Objects.equals(b, that.b)
                    && Objects.equals(c, that.c) && Objects.equals(d, that.d) && Objects.equals(e, that.e)
                    && Objects.equals(f, that.f) && Objects.equals(g, that.g) && Objects.equals(h, that.h);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), paletteSize, a, b, c, d, e, f, g, h);
        }
    }

    private static class ArrayPaletteStorage<T> extends CompactLongStorage<T> {
        private final int bitsPerEntry;
        private int paletteSize;
        private T[] palette;

        ArrayPaletteStorage(Class<?> contentType, NbtCompound root, IntToObjectFunction<T> paletteIndexer) {
            super(root);
            this.bitsPerEntry = root.getByte(POINTERS_BITS_PER_ENTRY_KEY);
            this.paletteSize = root.getShort(PALETTE_SIZE_KEY);
            this.palette = (T[]) Array.newInstance(contentType, 1 << bitsPerEntry);
            ByteArrayInputStream paletteStream = new ByteArrayInputStream(root.getByteArray(PALETTE_KEY));
            for (int i = 0; i < paletteSize; i++) {
                try {
                    this.palette[i] = paletteIndexer.apply(MapUtils.readVarInt(paletteStream));
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        }

        ArrayPaletteStorage(Class<?> contentType, int bitsPerEntry, int length, T[] paletteContent) {
            super(bitsPerEntry, length);
            this.bitsPerEntry = bitsPerEntry;
            this.paletteSize = paletteContent == null ? 0 : paletteContent.length;
            this.palette = (T[]) Array.newInstance(contentType, 1 << bitsPerEntry);
            if (paletteContent != null) {
                System.arraycopy(paletteContent, 0, this.palette, 0, paletteSize);
            }
        }

        @Override
        public boolean set(int index, T value) {
            int localPaletteSize = paletteSize;
            for (int palettePointer = 0; palettePointer < localPaletteSize; palettePointer++) {
                if (palette[palettePointer] == value) {
                    setPointerAt(bitsPerEntry, index, palettePointer);
                    return true;
                }
            }
            if (paletteSize < palette.length) {
                paletteSize++;
                palette[localPaletteSize] = value;
                setPointerAt(bitsPerEntry, index, localPaletteSize);
                return true;
            }
            return false;
        }

        @Override
        public T get(int index) {
            return palette[getPointerAt(bitsPerEntry, index)];
        }

        @Override
        public int getPaletteSize() {
            return paletteSize;
        }

        @Override
        public int getPaletteCapacity() {
            return palette.length;
        }

        @Override
        public StorageType getStorageType() {
            return StorageType.ARRAY;
        }

        @Override
        public int estimateMemoryUsage() {
            return super.estimateMemoryUsage() + 24 + 8 * palette.length;
        }

        @Override
        public InternalStorage<T> createLargerWith(Class<?> contentType, int length, T addedPaletteEntry) {
            if (MathHelper.ceilLog2(palette.length + 1) < MathHelper.ceilLog2(length)) {
                T[] content = (T[]) Array.newInstance(contentType, palette.length + 1);
                System.arraycopy(palette, 0, content, 0, palette.length);
                content[content.length - 1] = addedPaletteEntry;
                return new ArrayPaletteStorage<>(contentType, bitsPerEntry + 1, length, content).withPointers(bitsPerEntry, bitsPerEntry + 1, length, this);
            }
            ArrayRawStorage<T> newStorage = new ArrayRawStorage<>(contentType, length);
            for (int i = 0; i < length; i++) {
                newStorage.set(i, get(i));
            }
            return newStorage;
        }

        @Override
        protected T getPaletteEntry(int pointer) {
            return palette[pointer];
        }

        @Override
        public NbtCompound toNbt(ObjectToIntFunction<T> paletteIndexer) {
            NbtCompound root = super.toNbt(paletteIndexer);
            root.put(POINTERS_BITS_PER_ENTRY_KEY, NbtByte.of((byte) bitsPerEntry));
            root.put(PALETTE_SIZE_KEY, NbtShort.of((short) paletteSize));
            ByteArrayOutputStream paletteStream = new ByteArrayOutputStream(palette.length);
            for (int i = 0; i < paletteSize; i++) {
                try {
                    MapUtils.writeVarInt(paletteStream, paletteIndexer.apply(palette[i]));
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }
            root.put(PALETTE_KEY, new NbtByteArray(paletteStream.toByteArray()));
            return root;
        }

        @Override
        public Iterator<T> iterator(int startIndex) {
            return new CompactLongIterator(startIndex, bitsPerEntry);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            ArrayPaletteStorage<?> that = (ArrayPaletteStorage<?>) o;
            return bitsPerEntry == that.bitsPerEntry && paletteSize == that.paletteSize && Arrays.equals(palette,
                    that.palette);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(super.hashCode(), bitsPerEntry, paletteSize);
            result = 31 * result + Arrays.hashCode(palette);
            return result;
        }
    }

    private static class ArrayRawStorage<T> implements InternalStorage<T> {
        private final T[] content;

        ArrayRawStorage(Class<?> contentType, NbtCompound root, IntToObjectFunction<T> paletteIndexer) {
            this.content = (T[]) Array.newInstance(contentType, root.getShort(PALETTE_CAPACITY_KEY));
            ByteArrayInputStream paletteStream = new ByteArrayInputStream(root.getByteArray(PALETTE_KEY));
            for (int i = 0; i < this.content.length; i++) {
                try {
                    this.content[i] = paletteIndexer.apply(MapUtils.readVarInt(paletteStream));
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        }

        ArrayRawStorage(Class<?> contentType, int length) {
            this.content = (T[]) Array.newInstance(contentType, length);
        }

        @Override
        public boolean set(int index, T value) {
            content[index] = value;
            return true;
        }

        @Override
        public T get(int index) {
            return content[index];
        }

        @Override
        public int getPaletteSize() {
            return content.length;
        }

        @Override
        public int getPaletteCapacity() {
            return content.length;
        }

        @Override
        public StorageType getStorageType() {
            return StorageType.RAW;
        }

        @Override
        public int estimateMemoryUsage() {
            return 16 + 8 * content.length;
        }

        @Override
        public InternalStorage<T> createLargerWith(Class<?> contentType, int length, T addedPaletteEntry) {
            if (length <= content.length) {
                throw new IllegalArgumentException("Called createLarger with a length less than or equal to current length");
            }
            ArrayRawStorage<T> newStorage = new ArrayRawStorage<>(contentType, length);
            System.arraycopy(content, 0, newStorage.content, 0, content.length);
            return newStorage;
        }

        @Override
        public NbtCompound toNbt(ObjectToIntFunction<T> paletteIndexer) {
            NbtCompound root = new NbtCompound();
            root.put(PALETTE_CAPACITY_KEY, NbtShort.of((short) content.length));
            ByteArrayOutputStream paletteStream = new ByteArrayOutputStream(content.length);
            for (int i = 0; i < content.length; i++) {
                try {
                    MapUtils.writeVarInt(paletteStream, paletteIndexer.apply(content[i]));
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }
            root.put(PALETTE_KEY, new NbtByteArray(paletteStream.toByteArray()));
            return root;
        }

        public Iterator<T> iterator(int startIndex) {
            return ObjectIterators.wrap(content, startIndex, content.length - startIndex);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ArrayRawStorage<?> that = (ArrayRawStorage<?>) o;
            return Arrays.equals(content, that.content);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(content);
        }
    }

    private enum StorageType {
        SINGLE(SinglePaletteStorage::new),
        DOUBLE(DoublePaletteStorage::new),
        QUAD(QuadPaletteStorage::new),
        OCTO(OctoPaletteStorage::new),
        ARRAY(ArrayPaletteStorage::new),
        RAW(ArrayRawStorage::new);

        public final TriFunction<Class<?>, NbtCompound, IntToObjectFunction<?>, InternalStorage<?>> storageDeserializer;

        <T> StorageType(TriFunction<Class<?>, NbtCompound, IntToObjectFunction<?>, InternalStorage<?>> storageDeserializer) {
            this.storageDeserializer = storageDeserializer;
        }
    }

    private class LengthLimitedIterator<T> implements Iterator<T> {
        private final Iterator<T> wrapped;
        private final int changes;
        private final int size;
        private int count = 0;

        LengthLimitedIterator(Iterator<T> wrapped) {
            this.wrapped = wrapped;
            this.changes = PaletteStorage.this.changes;
            this.size = PaletteStorage.this.storageSize;
        }

        @Override
        public boolean hasNext() {
            return count < size && wrapped.hasNext();
        }

        @Override
        public T next() {
            if (PaletteStorage.this.changes != changes) {
                throw new ConcurrentModificationException();
            }
            return ++count <= size ? wrapped.next() : null;
        }
    }
}
