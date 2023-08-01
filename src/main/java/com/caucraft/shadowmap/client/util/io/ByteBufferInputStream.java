package com.caucraft.shadowmap.client.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class ByteBufferInputStream extends InputStream {
    private final ByteBuffer buffer;
    private boolean closed;

    public ByteBufferInputStream(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }

    @Override
    public int read() throws IOException {
        if (closed) {
            return -1;
        }
        if (!buffer.hasRemaining()) {
            return -1;
        }
        return buffer.get() & 0xFF;
    }

    @Override
    public int read(@NotNull byte[] b, int off, int len) throws IOException {
        if (closed) {
            return -1;
        }
        if (len > buffer.remaining()) {
            if (!buffer.hasRemaining()) {
                return -1;
            }
            len = buffer.remaining();
        }
        buffer.get(b, off, len);
        return len;
    }

    @Override
    public long skip(long n) throws IOException {
        if (closed) {
            return 0;
        }
        int offset = (int) Math.max(0, Math.min(n, buffer.remaining()));
        buffer.position(buffer.position() + offset);
        return offset;
    }

    @Override
    public int available() throws IOException {
        if (closed) {
            return 0;
        }
        return buffer.remaining();
    }

    @Override
    public synchronized void mark(int readlimit) {
        if (closed) {
            return;
        }
        buffer.mark();
    }

    @Override
    public synchronized void reset() throws IOException {
        if (closed) {
            return;
        }
        buffer.reset();
    }

    @Override
    public boolean markSupported() {
        return true;
    }
}
