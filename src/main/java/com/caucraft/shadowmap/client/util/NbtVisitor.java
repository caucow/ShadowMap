package com.caucraft.shadowmap.client.util;

import java.io.IOException;

public interface NbtVisitor {
    void beginCompound() throws IOException;
    void beginCompound(String key) throws IOException;
    void endCompound() throws IOException;
    void beginList(byte elementType, int size) throws IOException;
    void beginList(String key, byte elementType, int size) throws IOException;
    void endList() throws IOException;
    void visitByte(byte value) throws IOException;
    void visitByte(String key, byte value) throws IOException;
    void visitLongArray(long[] array) throws IOException;
    void visitLongArray(String key, long[] array) throws IOException;
}
