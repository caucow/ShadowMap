package com.caucraft.shadowmap.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;

import java.util.concurrent.atomic.AtomicInteger;

public class MapFramebuffer extends Framebuffer {
    private static final int WIDTH_SHIFT = 20;
    private static final int HEIGHT_SHIFT = 8;
    private static final int RESOLUTION_MASK = 0x0FFF;
    private static final int CLOSED = 0x01;
    private final AtomicInteger state;

    public MapFramebuffer(int width, int height) {
        super(false);
        this.state = new AtomicInteger(((width - 1) & RESOLUTION_MASK) << WIDTH_SHIFT | ((height - 1) & RESOLUTION_MASK) << HEIGHT_SHIFT);
    }

    public int getWidth() {
        return (state.get() >>> WIDTH_SHIFT & RESOLUTION_MASK) + 1;
    }

    public int getHeight() {
        return (state.get() >>> HEIGHT_SHIFT & RESOLUTION_MASK) + 1;
    }

    public void close() {
        state.updateAndGet((x) -> x | CLOSED);
        if (RenderSystem.isOnRenderThreadOrInit()) {
            this.delete();
        } else {
            RenderSystem.recordRenderCall(this::delete);
        }
    }

    public boolean isClosed() {
        return (state.get() & CLOSED) == CLOSED;
    }

    public boolean isInitialized() {
        return getColorAttachment() != -1;
    }

    @Override
    public void initFbo(int width, int height, boolean getError) {
        if (isClosed()) {
            return;
        }
        super.initFbo(width, height, getError);
    }
}
