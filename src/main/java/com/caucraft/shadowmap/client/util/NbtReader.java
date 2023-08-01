package com.caucraft.shadowmap.client.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.nbt.NbtElement;

import java.io.DataInputStream;
import java.io.IOException;

// A streaming nbt reader for RegionBitSet
// TODO extend for general purpose in the future
public class NbtReader {

    private DataInputStream in;
    private NbtVisitor visitor;
    private IntArrayList typeStack;
    private byte curType;
    private ExceptionalRunnable curHandler;

    public NbtReader(DataInputStream in, NbtVisitor visitor) {
        this.in = in;
        this.visitor = visitor;
        this.typeStack = new IntArrayList();
    }

    public void readFully() throws IOException {
        this.curType = in.readByte();
        switch (curType) {
            case NbtElement.COMPOUND_TYPE -> {
                visitor.beginCompound(in.readUTF());
                typeStack.push(curType);
            }
            case NbtElement.LIST_TYPE -> {
                String rootName = in.readUTF();
                byte elementType = in.readByte();
                int size = in.readInt();
                typeStack.push(elementType);
                typeStack.push(size);
                typeStack.push(curType);
                visitor.beginList(rootName, elementType, size);
            }
            default -> throw new IOException("Root element type was not compound or list");
        }

        while (!typeStack.isEmpty()) {
            curHandler.run();
        }
    }

    private void iterateCompound() throws IOException {
        byte nextType = in.readByte();
        if (nextType == NbtElement.END_TYPE) {
            visitor.endCompound();
            typeStack.popInt();
            onStackPop();
        }
        String key = in.readUTF();
        switch (nextType) {
            case NbtElement.BYTE_TYPE -> visitor.visitByte(key, in.readByte());
            case NbtElement.LONG_ARRAY_TYPE -> {
                long[] array = new long[in.readInt()];
                for (int i = 0; i < array.length; i++) {
                    array[i] = in.readLong();
                }
                visitor.visitLongArray(key, array);
            }
            case NbtElement.LIST_TYPE -> {
                byte elementType = in.readByte();
                int size = in.readInt();
                typeStack.push(elementType);
                typeStack.push(size);
                typeStack.push(nextType);
                curType = nextType;
                curHandler = this::iterateList;
                visitor.beginList(key, elementType, size);
            }
            case NbtElement.COMPOUND_TYPE -> {
                typeStack.push(nextType);
                curType = nextType;
                curHandler = this::iterateCompound;
                visitor.beginCompound(key);
            }
            default -> throw new UnsupportedOperationException("Tag type " + nextType + " not implemented yet.");
        }
    }

    private void iterateList() throws IOException {
        if (typeStack.peekInt(1) <= 0) {
            visitor.endList();
            typeStack.popInt();
            typeStack.popInt();
            typeStack.popInt();
            onStackPop();
            return;
        }
        typeStack.set(typeStack.size() - 2, typeStack.peekInt(1) - 1);
        byte nextType = (byte) typeStack.peekInt(2);
        switch (nextType) {
            case NbtElement.BYTE_TYPE -> visitor.visitByte(in.readByte());
            case NbtElement.LONG_ARRAY_TYPE -> {
                long[] array = new long[in.readInt()];
                for (int i = 0; i < array.length; i++) {
                    array[i] = in.readLong();
                }
                visitor.visitLongArray(array);
            }
            case NbtElement.LIST_TYPE -> {
                byte elementType = in.readByte();
                int size = in.readInt();
                typeStack.push(elementType);
                typeStack.push(size);
                typeStack.push(nextType);
                curType = nextType;
                curHandler = this::iterateList;
                visitor.beginList(elementType, size);
            }
            case NbtElement.COMPOUND_TYPE -> {
                typeStack.push(nextType);
                curType = nextType;
                curHandler = this::iterateCompound;
                visitor.beginCompound();
            }
            default -> throw new UnsupportedOperationException("Tag type " + nextType + " not implemented yet.");
        }
    }

    private void onStackPop() {
        if (typeStack.isEmpty()) {
            curType = 0;
            curHandler = null;
        }
        curType = (byte) typeStack.peekInt(0);
        curHandler = switch (curType) {
            case NbtElement.LIST_TYPE ->  this::iterateList;
            case NbtElement.COMPOUND_TYPE -> this::iterateCompound;
            default -> throw new IllegalStateException("Invalid tag type on stack: " + curType);
        };
    }

    @FunctionalInterface
    private interface ExceptionalRunnable {
        void run() throws IOException;
    }
}
