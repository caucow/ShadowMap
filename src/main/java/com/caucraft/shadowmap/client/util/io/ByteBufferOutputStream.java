package com.caucraft.shadowmap.client.util.io;

import com.caucraft.shadowmap.client.util.MapUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class ByteBufferOutputStream extends OutputStream {
    private ByteBuffer buffer;

    public ByteBufferOutputStream() {
        this.buffer = ByteBuffer.allocate(MapUtils.DEFAULT_BUFFER_SIZE);
    }

    public ByteBufferOutputStream(ByteBuffer initialBuffer) {
        this.buffer = initialBuffer;
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    @Override
    public void write(int b) throws IOException {
        if (!buffer.hasRemaining()) {
            try {
                buffer = MapUtils.growBuffer(buffer);
            } catch (IllegalArgumentException ex) {
                throw new IOException(ex);
            }
        }
        buffer.put((byte) b);
    }

    @Override
    public void write(@NotNull byte[] b, int off, int len) throws IOException {
        if (buffer.remaining() < len) {
            try {
                buffer = MapUtils.growBuffer(buffer, buffer.position() + len);
            } catch (IllegalArgumentException ex) {
                throw new IOException(ex);
            }
        }
        buffer.put(b, off, len);
    }
}
