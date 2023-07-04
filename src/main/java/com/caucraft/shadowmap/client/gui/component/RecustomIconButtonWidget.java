package com.caucraft.shadowmap.client.gui.component;

import com.caucraft.shadowmap.client.util.TextHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

public class RecustomIconButtonWidget extends ButtonWidget {

    private String message;
    private Sprite icon;
    private int textColor;

    public RecustomIconButtonWidget(int x, int y, int width, int height, String message, PressAction onPress) {
        super(x, y, width, height, Text.empty(), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.message = message;
        this.textColor = -1;
    }

    @Override
    public Text getMessage() {
        return Text.empty();
    }

    public void setText(String text) {
        this.message = text;
    }

    public String getText() {
        return message;
    }

    public String getDisplayText() {
        return getText();
    }

    public int getTextColor() {
        return textColor;
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
    }

    public Sprite getIcon() {
        return icon;
    }

    public void setIcon(Sprite icon) {
        this.icon = icon;
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrices = context.getMatrices();
        RenderSystem.enableDepthTest();
        super.renderButton(context, mouseX, mouseY, delta);
        RenderSystem.enableDepthTest();
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        String text = getDisplayText();
        Sprite icon = getIcon();
        int maxTextWidth = width - (icon == null ? 6 : 20); // 2x2p margin, 14p sprite, 2p gap
        int textWidth = text == null ? 0 : textRenderer.getWidth(text);
        if (text != null && textWidth > maxTextWidth) {
            text = textRenderer.trimToWidth(text, maxTextWidth - 6) + "...";
            textWidth = textRenderer.getWidth(text);
        }
        int x;
        if (icon == null) {
            x = getX() + width / 2 - textWidth / 2;
        } else if (text != null) {
            x = getX() + width / 2 - textWidth / 2 + 8;
        } else {
            x = getX() + width / 2 + 9;
        }
        if (text != null) {
            int textColor = getTextColor();
            if (!active) {
                textColor = textColor & 0xFF000000 | ((textColor >> 16 & 0xFF) * 160 / 255) << 16 | ((textColor >> 8 & 0xFF) * 160 / 255) << 8 | ((textColor & 0xFF) * 160 / 255);
            }
            TextHelper.get(textRenderer, matrices).shadow(true).color(textColor).draw(text, x, getY() + height / 2 - 4);
        }
        if (icon != null) {
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            x -= 16;
            int y = getY() + height / 2 - 7;
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buffer = tess.getBuffer();
            RenderSystem.enableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
            RenderSystem.setShaderTexture(0, icon.getAtlasId());
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
            buffer.vertex(matrix, x,       y,      0).texture(icon.getMinU(), icon.getMinV()).next();
            buffer.vertex(matrix, x,       y + 14, 0).texture(icon.getMinU(), icon.getMaxV()).next();
            buffer.vertex(matrix, x + 14,  y + 14, 0).texture(icon.getMaxU(), icon.getMaxV()).next();
            buffer.vertex(matrix, x + 14,  y,      0).texture(icon.getMaxU(), icon.getMinV()).next();
            tess.draw();
        }
    }
}
