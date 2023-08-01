package com.caucraft.shadowmap.client.gui.component;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

public abstract class FullscreenWidget extends ClickableWidget {

    protected int screenWidth, screenHeight;

    public FullscreenWidget(int x, int y, int width, int height, int screenWidth, int screenHeight, Text message) {
        super(x, y, width, height, message);
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    public void setScreenSize(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    @Override
    public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.getBuffer();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int x1 = 10;
        int x2 = screenWidth - 10;
        int y1 = 10;
        int y2 = screenHeight - 10;

        // middle solid
        buffer.vertex(x1 + 20, y1 + 20, 900).color(0, 0, 0, 224).next();
        buffer.vertex(x1 + 20, y2 - 20, 900).color(0, 0, 0, 224).next();
        buffer.vertex(x2 - 20, y2 - 20, 900).color(0, 0, 0, 224).next();
        buffer.vertex(x2 - 20, y1 + 20, 900).color(0, 0, 0, 224).next();
        // left gradient
        buffer.vertex(x1, y1, 900).color(0, 0, 0, 0).next();
        buffer.vertex(x1, y2, 900).color(0, 0, 0, 0).next();
        buffer.vertex(x1 + 20, y2 - 20, 900).color(0, 0, 0, 224).next();
        buffer.vertex(x1 + 20, y1 + 20, 900).color(0, 0, 0, 224).next();
        // right gradient
        buffer.vertex(x2 - 20, y1 + 20, 900).color(0, 0, 0, 224).next();
        buffer.vertex(x2 - 20, y2 - 20, 900).color(0, 0, 0, 224).next();
        buffer.vertex(x2, y2, 900).color(0, 0, 0, 0).next();
        buffer.vertex(x2, y1, 900).color(0, 0, 0, 0).next();
        // top gradient
        buffer.vertex(x1, y1, 900).color(0, 0, 0, 0).next();
        buffer.vertex(x1 + 20, y1 + 20, 900).color(0, 0, 0, 224).next();
        buffer.vertex(x2 - 20, y1 + 20, 900).color(0, 0, 0, 224).next();
        buffer.vertex(x2, y1, 900).color(0, 0, 0, 0).next();
        // bottom gradient
        buffer.vertex(x1 + 20, y2 - 20, 900).color(0, 0, 0, 224).next();
        buffer.vertex(x1, y2, 900).color(0, 0, 0, 0).next();
        buffer.vertex(x2, y2, 900).color(0, 0, 0, 0).next();
        buffer.vertex(x2 - 20, y2 - 20, 900).color(0, 0, 0, 224).next();

        tess.draw();
    }
}
