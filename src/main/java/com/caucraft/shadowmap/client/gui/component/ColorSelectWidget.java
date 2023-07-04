package com.caucraft.shadowmap.client.gui.component;

import com.caucraft.shadowmap.api.ui.MapRenderContext;
import com.caucraft.shadowmap.client.util.MapUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.function.Consumer;

public class ColorSelectWidget extends ClickableWidget {

    private final Consumer<ColorSelectWidget> onColorSelect;
    private boolean expanded;
    private int color;
    private float hue, sat, val;
    private MapRenderContext.RectangleD clickBounds;

    public ColorSelectWidget(int x, int y, int width, int height, int colorRGB, Consumer<ColorSelectWidget> onColorSelect) {
        super(x, y, width, height, Text.empty());
        this.onColorSelect = onColorSelect;
        setColor(colorRGB);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean value = super.mouseClicked(mouseX, mouseY, button);
        if (!value) {
            expanded = false;
            clickBounds = null;
        }
        return value;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (super.isMouseOver(mouseX, mouseY)) {
            return true;
        }
        if (!expanded) {
            return false;
        }
        int x = getX() + width / 2;
        int y = getY() + height + 1;
        return mouseX >= x - 40 && mouseX <= x + 40 && mouseY >= y && mouseY <= y + 95;
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        return active && visible && isMouseOver(mouseX, mouseY);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        int x = getX();
        int y = getY();
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            expanded = !expanded;
            return;
        }
        x += width / 2 - 36;
        y += height + 5;
        if (mouseX >= x && mouseX <= x + 72 && mouseY >= y && mouseY <= y + 12) {
            clickBounds = new MapRenderContext.RectangleD(x, y, x + 72, y + 12);
            updateColorFromMouse(mouseX, mouseY);
            return;
        }
        y += 15;
        if (mouseX >= x && mouseX <= x + 72 && mouseY >= y && mouseY <= y + 72) {
            clickBounds = new MapRenderContext.RectangleD(x, y, x + 72, y + 72);
            updateColorFromMouse(mouseX, mouseY);
            return;
        }
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (!expanded) {
            return;
        }
        updateColorFromMouse(mouseX, mouseY);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        if (!expanded) {
            return;
        }
        updateColorFromMouse(mouseX, mouseY);
        if (!clicked(mouseX, mouseY) && clickBounds == null) {
            expanded = false;
        }
        clickBounds = null;
    }

    private void updateColorFromMouse(double mouseX, double mouseY) {
        if (clickBounds == null) {
            return;
        }
        if (clickBounds.w != clickBounds.h) {
            color = MapUtils.hsvToRgb(
                    this.hue = (float) ((mouseX - clickBounds.x1) / clickBounds.w),
                    sat,
                    val);
            onColorSelect.accept(this);
        } else {
            color = MapUtils.hsvToRgb(
                    hue,
                    this.sat = (float) ((mouseX - clickBounds.x1) / clickBounds.w),
                    this.val = 1.0F - (float) ((mouseY - clickBounds.z1) / clickBounds.h));
            onColorSelect.accept(this);
        }
    }

    public int getColor() {
        return color;
    }

    public void setColor(int colorRGB) {
        this.color = colorRGB;
        float[] hsv = MapUtils.rgbToHsv(colorRGB);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.getBuffer();
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // Border, Color
        int x1 = x;
        int x2 = x + width;
        int y1 = y;
        int y2 = y + height;
        int r = 128;
        int g = 128;
        int b = 128;
        if (hovered) {
            r = g = b = 255;
        }
        buffer.vertex(x1, y1, 10).color(r, g, b, 255).next();
        buffer.vertex(x1, y2, 10).color(r, g, b, 255).next();
        buffer.vertex(x2, y2, 10).color(r, g, b, 255).next();
        buffer.vertex(x2, y1, 10).color(r, g, b, 255).next();
        x1++;
        x2--;
        y1++;
        y2--;
        r = color >> 16 & 0xFF;
        g = color >> 8 & 0xFF;
        b = color & 0xFF;
        buffer.vertex(x1, y1, 10).color(r, g, b, 255).next();
        buffer.vertex(x1, y2, 10).color(r, g, b, 255).next();
        buffer.vertex(x2, y2, 10).color(r, g, b, 255).next();
        buffer.vertex(x2, y1, 10).color(r, g, b, 255).next();

        if (!expanded) {
            tess.draw();
            return;
        }

        // Popup Border, Background
        x += width / 2;
        y += getHeight() + 1;
        x1 = x - 40;
        x2 = x + 40;
        y1 = y;
        y2 = y + 95;
        buffer.vertex(x1, y1, 10).color(255, 255, 255, 255).next();
        buffer.vertex(x1, y2, 10).color(255, 255, 255, 255).next();
        buffer.vertex(x2, y2, 10).color(255, 255, 255, 255).next();
        buffer.vertex(x2, y1, 10).color(255, 255, 255, 255).next();
        x1++;
        x2--;
        y1++;
        y2--;
        buffer.vertex(x1, y1, 10).color(0, 0, 0, 255).next();
        buffer.vertex(x1, y2, 10).color(0, 0, 0, 255).next();
        buffer.vertex(x2, y2, 10).color(0, 0, 0, 255).next();
        buffer.vertex(x2, y1, 10).color(0, 0, 0, 255).next();

        // Hue selector bar
        x1 += 3;
        x2 -= 3;
        y1 += 3;
        y2 = y1 + 12;
        for (int i = 0; i < 6; i++) {
            int color1 = MapUtils.hsvToRgb(i / 6.0F, 1.0F, 1.0F);
            int color2 = MapUtils.hsvToRgb((i + 1) / 6.0F, 1.0F, 1.0F);
            r = color1 >> 16 & 0xFF;
            g = color1 >> 8 & 0xFF;
            b = color1 & 0xFF;
            int r2 = color2 >> 16 & 0xFF;
            int g2 = color2 >> 8 & 0xFF;
            int b2 = color2 & 0xFF;
            x1 = x - 36 + 12 * i;
            x2 = x1 + 12;
            buffer.vertex(x1, y1, 10).color(r, g, b, 255).next();
            buffer.vertex(x1, y2, 10).color(r, g, b, 255).next();
            buffer.vertex(x2, y2, 10).color(r2, g2, b2, 255).next();
            buffer.vertex(x2, y1, 10).color(r2, g2, b2, 255).next();
        }

        // Saturation/Value Square
        x1 = x - 36;
        x2 = x1 + 72;
        y1 = y2 + 3;
        y2 = y1 + 72;
        int localColor = MapUtils.hsvToRgb(hue, 1.0F, 1.0F);
        r = localColor >> 16 & 0xFF;
        g = localColor >> 8 & 0xFF;
        b = localColor & 0xFF;
        buffer.vertex(x1, y1, 10).color(r, g, b, 255).next();
        buffer.vertex(x1, y2, 10).color(r, g, b, 255).next();
        buffer.vertex(x2, y2, 10).color(r, g, b, 255).next();
        buffer.vertex(x2, y1, 10).color(r, g, b, 255).next();
        buffer.vertex(x1, y1, 10).color(255, 255, 255, 255).next();
        buffer.vertex(x1, y2, 10).color(255, 255, 255, 255).next();
        buffer.vertex(x2, y2, 10).color(255, 255, 255, 0).next();
        buffer.vertex(x2, y1, 10).color(255, 255, 255, 0).next();
        buffer.vertex(x1, y1, 10).color(0, 0, 0, 0).next();
        buffer.vertex(x1, y2, 10).color(0, 0, 0, 255).next();
        buffer.vertex(x2, y2, 10).color(0, 0, 0, 255).next();
        buffer.vertex(x2, y1, 10).color(0, 0, 0, 0).next();
        tess.draw();

        // Click boxes
        if (this.clickBounds != null) {
            buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            buffer.vertex(clickBounds.x1, clickBounds.z1, 10).color(255, 255, 255, 255).next();
            buffer.vertex(clickBounds.x1, clickBounds.z2, 10).color(255, 255, 255, 255).next();
            buffer.vertex(clickBounds.x1, clickBounds.z2, 10).color(255, 255, 255, 255).next();
            buffer.vertex(clickBounds.x2, clickBounds.z2, 10).color(255, 255, 255, 255).next();
            buffer.vertex(clickBounds.x2, clickBounds.z2, 10).color(255, 255, 255, 255).next();
            buffer.vertex(clickBounds.x2, clickBounds.z1, 10).color(255, 255, 255, 255).next();
            buffer.vertex(clickBounds.x2, clickBounds.z1, 10).color(255, 255, 255, 255).next();
            buffer.vertex(clickBounds.x1, clickBounds.z1, 10).color(255, 255, 255, 255).next();
            mouseX = (int) MathHelper.clamp(mouseX, clickBounds.x1, clickBounds.x2);
            mouseY = (int) MathHelper.clamp(mouseY, clickBounds.z1, clickBounds.z2);
            buffer.vertex(mouseX - 1, mouseY - 1, 10).color(255, 255, 255, 255).next();
            buffer.vertex(mouseX - 1, mouseY + 1, 10).color(255, 255, 255, 255).next();
            buffer.vertex(mouseX - 1, mouseY + 1, 10).color(255, 255, 255, 255).next();
            buffer.vertex(mouseX + 1, mouseY + 1, 10).color(255, 255, 255, 255).next();
            buffer.vertex(mouseX + 1, mouseY + 1, 10).color(255, 255, 255, 255).next();
            buffer.vertex(mouseX + 1, mouseY - 1, 10).color(255, 255, 255, 255).next();
            buffer.vertex(mouseX + 1, mouseY - 1, 10).color(255, 255, 255, 255).next();
            buffer.vertex(mouseX - 1, mouseY - 1, 10).color(255, 255, 255, 255).next();
            tess.draw();
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}
}
