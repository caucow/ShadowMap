package com.caucraft.shadowmap.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

public class TextHelper {
    private TextRenderer renderer;
    private VertexConsumerProvider.Immediate vbuffer;
    private Matrix4f matrix;
    private int color;
    private int backgroundColor;
    private boolean shadow;
    private boolean depthTest;
    private int light;
    private boolean reverse;
    private boolean immediate;

    private TextHelper(TextRenderer renderer, VertexConsumerProvider.Immediate vbuffer, Matrix4f matrix) {
        this.renderer = renderer;
        this.vbuffer = vbuffer;
        this.matrix = matrix;
        this.color = 0xFFFF_FFFF;
        this.backgroundColor = 0;
        this.shadow = false;
        this.depthTest = true;
        this.light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        this.reverse = false;
        this.immediate = true;
    }

    /**
     * Draws the provided text left-aligned at the provided coordinates.
     * This is just an alias for {@link #draw(String, float, float)}
     * @param text the text to draw.
     * @param x x coordinate of the left edge of the text.
     * @param y y coordinate of the top edge of the text.
     * @see #draw(String, float, float)
     */
    public void drawLeftAlign(String text, float x, float y) {
        draw(text, x, y);
    }

    /**
     * Draws the provided text center-aligned at the provided coordinates.
     * @param text the text to draw.
     * @param x x coordinate of the center of the text.
     * @param y y coordinate of the top edge of the text.
     * @param width a pre-computed value to use as the width.
     * @see #draw(String, float, float)
     */
    public void drawCentered(String text, float x, float y, int width) {
        draw(text, x - width * 0.5F, y);
    }

    /**
     * Draws the provided text center-aligned at the provided coordinates.
     * @param text the text to draw.
     * @param x x coordinate of the center of the text.
     * @param y y coordinate of the top edge of the text.
     * @see #draw(String, float, float)
     */
    public void drawCentered(String text, float x, float y) {
        drawCentered(text, x, y, renderer.getWidth(text));
    }

    /**
     * Draws the provided text right-aligned at the provided coordinates.
     * @param text the text to draw.
     * @param x x coordinate of the right edge of the text.
     * @param y y coordinate of the top edge of the text.
     * @param width a pre-computed value to use as the width.
     * @see #draw(String, float, float)
     */
    public void drawRightAlign(String text, float x, float y, int width) {
        draw(text, x - width, y);
    }

    /**
     * Draws the provided text right-aligned at the provided coordinates.
     * @param text the text to draw.
     * @param x x coordinate of the right edge of the text.
     * @param y y coordinate of the top edge of the text.
     * @see #draw(String, float, float)
     */
    public void drawRightAlign(String text, float x, float y) {
        drawRightAlign(text, x, y, renderer.getWidth(text));
    }

    /**
     * Draws the provided text arbitrarily horizontally aligned at the provided
     * coordinates.
     * @param text the text to draw.
     * @param x x coordinate of the right edge of the text.
     * @param y y coordinate of the top edge of the text.
     * @param width a pre-computed value to use as the width.
     * @param alignment a multiplier of the text's width to subtract from the x
     * coordinate. Providing 0.0 would result in left alignment, 0.5 center,
     * and 1.0 right.
     * @see #draw(String, float, float)
     */
    public void drawAligned(String text, float x, float y, int width, float alignment) {
        draw(text, x - width * alignment, y);
    }

    /**
     * Draws the provided text arbitrarily horizontally aligned at the provided
     * coordinates.
     * @param text the text to draw.
     * @param x x coordinate of the right edge of the text.
     * @param y y coordinate of the top edge of the text.
     * @param alignment a multiplier of the text's width to subtract from the x
     * coordinate. Providing 0.0 would result in left alignment, 0.5 center,
     * and 1.0 right.
     * @see #draw(String, float, float)
     */
    public void drawAligned(String text, float x, float y, float alignment) {
        drawAligned(text, x, y, renderer.getWidth(text), alignment);
    }

    /**
     * Draws the provided text left-aligned at the provided coordinates.
     * This is just an alias for {@link #draw(Text, float, float)}
     * @param text the text to draw.
     * @param x x coordinate of the left edge of the text.
     * @param y y coordinate of the top edge of the text.
     * @see #draw(Text, float, float)
     */
    public void drawLeftAlign(Text text, float x, float y) {
        draw(text.asOrderedText(), x, y);
    }

    /**
     * Draws the provided text center-aligned at the provided coordinates.
     * @param text the text to draw.
     * @param x x coordinate of the center of the text.
     * @param y y coordinate of the top edge of the text.
     * @param width a pre-computed value to use as the width.
     * @see #draw(Text, float, float)
     */
    public void drawCentered(Text text, float x, float y, int width) {
        draw(text.asOrderedText(), x - width * 0.5F, y);
    }

    /**
     * Draws the provided text center-aligned at the provided coordinates.
     * @param text the text to draw.
     * @param x x coordinate of the center of the text.
     * @param y y coordinate of the top edge of the text.
     * @see #draw(Text, float, float)
     */
    public void drawCentered(Text text, float x, float y) {
        drawCentered(text.asOrderedText(), x, y, renderer.getWidth(text));
    }

    /**
     * Draws the provided text right-aligned at the provided coordinates.
     * @param text the text to draw.
     * @param x x coordinate of the right edge of the text.
     * @param y y coordinate of the top edge of the text.
     * @param width a pre-computed value to use as the width.
     * @see #draw(Text, float, float)
     */
    public void drawRightAlign(Text text, float x, float y, int width) {
        draw(text.asOrderedText(), x - width, y);
    }

    /**
     * Draws the provided text right-aligned at the provided coordinates.
     * @param text the text to draw.
     * @param x x coordinate of the right edge of the text.
     * @param y y coordinate of the top edge of the text.
     * @see #draw(Text, float, float)
     */
    public void drawRightAlign(Text text, float x, float y) {
        drawRightAlign(text.asOrderedText(), x, y, renderer.getWidth(text));
    }

    /**
     * Draws the provided text arbitrarily horizontally aligned at the provided
     * coordinates.
     * @param text the text to draw.
     * @param x x coordinate of the right edge of the text.
     * @param y y coordinate of the top edge of the text.
     * @param width a pre-computed value to use as the width.
     * @param alignment a multiplier of the text's width to subtract from the x
     * coordinate. Providing 0.0 would result in left alignment, 0.5 center,
     * and 1.0 right.
     * @see #draw(Text, float, float)
     */
    public void drawAligned(Text text, float x, float y, int width, float alignment) {
        draw(text.asOrderedText(), x - width * alignment, y);
    }

    /**
     * Draws the provided text arbitrarily horizontally aligned at the provided
     * coordinates.
     * @param text the text to draw.
     * @param x x coordinate of the right edge of the text.
     * @param y y coordinate of the top edge of the text.
     * @param alignment a multiplier of the text's width to subtract from the x
     * coordinate. Providing 0.0 would result in left alignment, 0.5 center,
     * and 1.0 right.
     * @see #draw(Text, float, float)
     */
    public void drawAligned(Text text, float x, float y, float alignment) {
        drawAligned(text, x, y, renderer.getWidth(text), alignment);
    }

    /**
     * Draws the provided text left-aligned at the provided coordinates.
     * This is just an alias for {@link #draw(OrderedText, float, float)}
     * @param text the text to draw.
     * @param x x coordinate of the left edge of the text.
     * @param y y coordinate of the top edge of the text.
     * @see #draw(OrderedText, float, float)
     */
    public void drawLeftAlign(OrderedText text, float x, float y) {
        draw(text, x, y);
    }

    /**
     * Draws the provided text center-aligned at the provided coordinates.
     * @param text the text to draw.
     * @param x x coordinate of the center of the text.
     * @param y y coordinate of the top edge of the text.
     * @param width a pre-computed value to use as the width.
     * @see #draw(OrderedText, float, float)
     */
    public void drawCentered(OrderedText text, float x, float y, int width) {
        draw(text, x - width * 0.5F, y);
    }

    /**
     * Draws the provided text center-aligned at the provided coordinates.
     * @param text the text to draw.
     * @param x x coordinate of the center of the text.
     * @param y y coordinate of the top edge of the text.
     * @see #draw(OrderedText, float, float)
     */
    public void drawCentered(OrderedText text, float x, float y) {
        drawCentered(text, x, y, renderer.getWidth(text));
    }

    /**
     * Draws the provided text right-aligned at the provided coordinates.
     * @param text the text to draw.
     * @param x x coordinate of the right edge of the text.
     * @param y y coordinate of the top edge of the text.
     * @param width a pre-computed value to use as the width.
     * @see #draw(OrderedText, float, float)
     */
    public void drawRightAlign(OrderedText text, float x, float y, int width) {
        draw(text, x - width, y);
    }

    /**
     * Draws the provided text right-aligned at the provided coordinates.
     * @param text the text to draw.
     * @param x x coordinate of the right edge of the text.
     * @param y y coordinate of the top edge of the text.
     * @see #draw(OrderedText, float, float)
     */
    public void drawRightAlign(OrderedText text, float x, float y) {
        drawRightAlign(text, x, y, renderer.getWidth(text));
    }

    /**
     * Draws the provided text arbitrarily horizontally aligned at the provided
     * coordinates.
     * @param text the text to draw.
     * @param x x coordinate of the right edge of the text.
     * @param y y coordinate of the top edge of the text.
     * @param width a pre-computed value to use as the width.
     * @param alignment a multiplier of the text's width to subtract from the x
     * coordinate. Providing 0.0 would result in left alignment, 0.5 center,
     * and 1.0 right.
     * @see #draw(OrderedText, float, float)
     */
    public void drawAligned(OrderedText text, float x, float y, int width, float alignment) {
        draw(text, x - width * alignment, y);
    }

    /**
     * Draws the provided text arbitrarily horizontally aligned at the provided
     * coordinates.
     * @param text the text to draw.
     * @param x x coordinate of the right edge of the text.
     * @param y y coordinate of the top edge of the text.
     * @param alignment a multiplier of the text's width to subtract from the x
     * coordinate. Providing 0.0 would result in left alignment, 0.5 center,
     * and 1.0 right.
     * @see #draw(OrderedText, float, float)
     */
    public void drawAligned(OrderedText text, float x, float y, float alignment) {
        drawAligned(text, x, y, renderer.getWidth(text), alignment);
    }

    /**
     * Draws the provided text left-aligned at the provided coordinates. If the
     * helper is set to {@code immediate} mode, also calls {@code draw()} on the
     * vertex buffer.
     * @param text the text to draw.
     * @param x x coordinate of the left edge of the text.
     * @param y y coordinate of the top edge of the text.
     */
    public void draw(String text, float x, float y) {
        renderer.draw(text, x, y, color, shadow, matrix, vbuffer, depthTest ? TextRenderer.TextLayerType.NORMAL : TextRenderer.TextLayerType.SEE_THROUGH, backgroundColor, light, reverse);
        if (immediate) {
            vbuffer.draw();
        }
    }

    /**
     * Draws the provided text left-aligned at the provided coordinates. If the
     * helper is set to {@code immediate} mode, also calls {@code draw()} on the
     * vertex buffer.
     * @param text the text to draw.
     * @param x x coordinate of the left edge of the text.
     * @param y y coordinate of the top edge of the text.
     */
    public void draw(Text text, float x, float y) {
        draw(text.asOrderedText(), x, y);
    }

    /**
     * Draws the provided text left-aligned at the provided coordinates. If the
     * helper is set to {@code immediate} mode, also calls {@code draw()} on the
     * vertex buffer.
     * @param text the text to draw.
     * @param x x coordinate of the left edge of the text.
     * @param y y coordinate of the top edge of the text.
     */
    public void draw(OrderedText text, float x, float y) {
        renderer.draw(text, x, y, color, shadow, matrix, vbuffer, depthTest ? TextRenderer.TextLayerType.NORMAL : TextRenderer.TextLayerType.SEE_THROUGH, backgroundColor, light);
        if (immediate) {
            vbuffer.draw();
        }
    }

    /**
     * Calls {@code draw()} on the vertex buffer. This should be called after
     * all text drawing calls when the helper is not set to {@code immediate}
     * mode.
     */
    public void flushBuffers() {
        vbuffer.draw();
    }

    /**
     * @return the {@link TextRenderer} used by this helper.
     */
    public TextRenderer renderer() {
        return renderer;
    }

    /**
     * Sets the {@link TextRenderer} used by this helper.
     * @param renderer the new {@link TextRenderer}
     * @return this helper.
     */
    public TextHelper renderer(TextRenderer renderer) {
        this.renderer = renderer;
        return this;
    }

    /**
     * @return the vertex buffer currently used by this helper.
     */
    public VertexConsumerProvider.Immediate buffer() {
        return vbuffer;
    }

    /**
     * Sets the vertex buffer used by future draw calls with this helper.
     * Note that if the previous buffer is active, it will need to be flushed
     * separately.
     * @param vbuffer the new vertex buffer.
     * @return this helper.
     */
    public TextHelper buffer(VertexConsumerProvider.Immediate vbuffer) {
        this.vbuffer = vbuffer;
        return this;
    }

    /**
     * @return the transformation matrix currently used by this helper.
     */
    public Matrix4f matrix() {
        return matrix;
    }

    /**
     * Sets the transformation matrix used by future draw calls with this
     * helper.
     * @param matrix the new transformation matrix.
     * @return this helper.
     */
    public TextHelper matrix(Matrix4f matrix) {
        this.matrix = matrix;
        return this;
    }

    /**
     * @return the currently set text color.
     */
    public int color() {
        return color;
    }

    /**
     * Sets the color used by future draw calls with this helper.
     * @param color the new text color in {@code 0xAARRGGBB} format.
     * @return this helper.
     */
    public TextHelper color(int color) {
        this.color = color;
        return this;
    }

    /**
     * @return the currently set background color.
     */
    public int backgroundColor() {
        return backgroundColor;
    }

    /**
     * Sets the background color used by future draw calls with this helper.
     * @param backgroundColor the new background color in {@code 0xAARRGGBB}
     * format.
     * @return this helper.
     */
    public TextHelper backgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
        return this;
    }

    /**
     * @return whether shadow rendering is enabled in the helper.
     */
    public boolean shadow() {
        return shadow;
    }

    /**
     * Sets whether future draw calls with this helper draw text shadows.
     * @param shadow the new shadow setting.
     * @return this helper.
     */
    public TextHelper shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    /**
     * @return whether the helper is using depth testing.
     */
    public boolean depthTest() {
        return depthTest;
    }

    /**
     * Sets whether future draw calls with this helper should enable depth
     * testing.
     * @param depthTest the new depth test setting.
     * @return this helper.
     */
    public TextHelper depthTest(boolean depthTest) {
        this.depthTest = depthTest;
        return this;
    }

    /**
     * @return the currently set lighting value.
     */
    public int light() {
        return light;
    }

    /**
     * Sets the light value for future draw calls with this helper.
     * @param light the new light value.
     * @return this helper.
     */
    public TextHelper light(int light) {
        this.light = light;
        return this;
    }

    /**
     * @return whether the helper renders text right-to-left.
     */
    public boolean reverse() {
        return reverse;
    }

    /**
     * Sets reverse mode for future draw calls with this helper. If reverse mode
     * is set, text will be drawn in right-to-left instead of left-to-right.
     * @param reverse the new reverse mode setting.
     * @return this helper.
     */
    public TextHelper reverse(boolean reverse) {
        this.reverse = reverse;
        return this;
    }

    /**
     * @return whether to immediately draw the buffers after each draw-text
     * call.
     */
    public boolean immediate() {
        return immediate;
    }

    /**
     * Sets immediate mode for future draw calls with this helper. If immediate
     * mode is set, every draw-text call will also call the buffer's draw
     * method.
     * @param immediate the new immediate mode setting.
     * @return this helper.
     */
    public TextHelper immediate(boolean immediate) {
        this.immediate = immediate;
        return this;
    }

    /**
     * @return A new TextHelper
     */
    public static TextHelper get() {
        MinecraftClient client = MinecraftClient.getInstance();
        return get(
                client.textRenderer,
                client.getBufferBuilders().getEntityVertexConsumers(),
                new Matrix4f()
        );
    }

    /**
     * @return A new TextHelper
     */
    public static TextHelper get(TextRenderer renderer) {
        return get(renderer, new Matrix4f());
    }

    /**
     * @return A new TextHelper
     */
    public static TextHelper get(MatrixStack matrices) {
        MinecraftClient client = MinecraftClient.getInstance();
        return get(
                client.textRenderer,
                client.getBufferBuilders().getEntityVertexConsumers(),
                matrices.peek().getPositionMatrix()
        );
    }

    /**
     * @return A new TextHelper
     */
    public static TextHelper get(TextRenderer renderer, MatrixStack matrices) {
        MinecraftClient client = MinecraftClient.getInstance();
        return get(
                renderer,
                client.getBufferBuilders().getEntityVertexConsumers(),
                matrices.peek().getPositionMatrix()
        );
    }

    /**
     * @return A new TextHelper
     */
    public static TextHelper get(Matrix4f matrix) {
        MinecraftClient client = MinecraftClient.getInstance();
        return get(
                client.textRenderer,
                client.getBufferBuilders().getEntityVertexConsumers(),
                matrix
        );
    }

    /**
     * @return A new TextHelper
     */
    public static TextHelper get(TextRenderer renderer, Matrix4f matrix) {
        MinecraftClient client = MinecraftClient.getInstance();
        return get(
                renderer,
                client.getBufferBuilders().getEntityVertexConsumers(),
                matrix
        );
    }

    /**
     * @return A new TextHelper
     */
    public static TextHelper get(TextRenderer renderer, VertexConsumerProvider.Immediate vbuffer, MatrixStack matrices) {
        return get(renderer, vbuffer, matrices.peek().getPositionMatrix());
    }

    /**
     * @return A new TextHelper
     */
    public static TextHelper get(TextRenderer renderer, VertexConsumerProvider.Immediate vbuffer, Matrix4f matrix) {
        return new TextHelper(
                renderer,
                vbuffer,
                matrix
        );
    }
}
