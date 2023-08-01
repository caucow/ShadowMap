package com.caucraft.shadowmap.client.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.nbt.NbtElement;

import java.io.DataOutputStream;
import java.io.IOException;

// A streaming nbt writer for RegionBitSet
// TODO extend for general purpose in the future
public class NbtWriter implements AutoCloseable {
    private IntArrayList typeStack;
    private DataOutputStream out;
    private byte curType;

    public NbtWriter(DataOutputStream outputStream, String rootName) throws IOException {
        this.typeStack = new IntArrayList();
        this.out = outputStream;
        typeStack.push(this.curType = NbtElement.COMPOUND_TYPE);
        outputStream.write(NbtElement.COMPOUND_TYPE);
        outputStream.writeUTF(rootName);
    }

    @Override
    public void close() throws IOException {
        endCompound();
        if (!typeStack.isEmpty()) {
            throw new IllegalStateException("Tried to close NbtWriter before ending all tags");
        }
    }

    private void startCompoundEntry(byte elementType, String key) throws IOException {
        if (curType != NbtElement.COMPOUND_TYPE) {
            throw new IllegalStateException("Tried to write compound entry, but current tag is not a compound");
        }
        out.write(elementType);
        out.writeUTF(key);
    }

    private void startListElement(byte elementType) {
        if (curType != NbtElement.LIST_TYPE) {
            throw new IllegalStateException("Tried to write list element, but current tag is not a list");
        }
        if (typeStack.peekInt(2) != elementType) {
            throw new IllegalStateException(
                    "Tried to write element of type " + elementType + " but list was expecting "
                            + typeStack.peekInt(2));
        }
        if (typeStack.peekInt(1) <= 0) {
            throw new IllegalStateException("Tried to write element but list was full");
        }
        typeStack.set(typeStack.size() - 2, typeStack.peekInt(1) - 1);
    }

    public void beginCompound() throws IOException {
        startListElement(NbtElement.COMPOUND_TYPE);
        typeStack.push(curType = NbtElement.COMPOUND_TYPE);
    }

    public void beginCompound(String key) throws IOException {
        startCompoundEntry(NbtElement.COMPOUND_TYPE, key);
        typeStack.push(curType = NbtElement.COMPOUND_TYPE);
    }

    public void endCompound() throws IOException {
        if (curType != NbtElement.COMPOUND_TYPE) {
            throw new IllegalStateException("Tried to close compound, but open tag is " + curType);
        }
        typeStack.popInt();
        curType = typeStack.isEmpty() ? 0 : (byte) typeStack.peekInt(0);
        out.write(NbtElement.END_TYPE);
    }

    public void beginList(byte elementType, int size) throws IOException {
        startListElement(NbtElement.LIST_TYPE);
        typeStack.push(elementType);
        typeStack.push(size);
        typeStack.push(curType = NbtElement.LIST_TYPE);
        out.write(elementType);
        out.writeInt(size);
    }

    public void beginList(String key, byte elementType, int size) throws IOException {
        startCompoundEntry(NbtElement.LIST_TYPE, key);
        typeStack.push(elementType);
        typeStack.push(size);
        typeStack.push(curType = NbtElement.LIST_TYPE);
        out.write(elementType);
        out.writeInt(size);
    }

    public void endList() {
        if (curType != NbtElement.LIST_TYPE) {
            throw new IllegalStateException("Tried to close list, but open tag is " + curType);
        }
        int size = typeStack.peekInt(1);
        if (size != 0) {
            throw new IllegalStateException("Tried to close list, but was expecting " + size + " more elements");
        }
        typeStack.popInt();
        typeStack.popInt();
        typeStack.popInt();
        curType = typeStack.isEmpty() ? 0 : (byte) typeStack.peekInt(0);
    }

    public void writeByte(byte value) throws IOException {
        startListElement(NbtElement.BYTE_TYPE);
        out.write(value);
    }

    public void writeByte(String key, byte value) throws IOException {
        startCompoundEntry(NbtElement.BYTE_TYPE, key);
        out.write(value);
    }

    public void writeLongArray(long[] array) throws IOException {
        startListElement(NbtElement.LONG_ARRAY_TYPE);
        out.writeInt(array.length);
        for (int i = 0; i < array.length; i++) {
            out.writeLong(array[i]);
        }
    }

    public void writeLongArray(String key, long[] array) throws IOException {
        startCompoundEntry(NbtElement.LONG_ARRAY_TYPE, key);
        out.writeInt(array.length);
        for (int i = 0; i < array.length; i++) {
            out.writeLong(array[i]);
        }
    }
}
