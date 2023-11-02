package com.caucraft.shadowmap.client.gui.component;

import com.caucraft.shadowmap.client.util.TextHelper;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;

public class ConfirmDialogWidget extends FullscreenWidget {

    private final TextRenderer textRenderer;
    private final Consumer<ConfirmDialogWidget> closeAndRemoveFunction;
    private final List<OrderedText> prompt;
    private final int promptWidth;
    private final RecustomIconButtonWidget[] buttons;

    public ConfirmDialogWidget(int x, int y, int width, int height, int screenWidth, int screenHeight, TextRenderer textRenderer, Text message, Consumer<ConfirmDialogWidget> closeAndRemoveFunction, Option... options) {
        super(x, y, width, height, screenWidth, screenHeight, message);
        this.textRenderer = textRenderer;
        this.prompt = textRenderer.wrapLines(message, width - 40);
        int pwidth = 0;
        for (OrderedText text : prompt) {
            pwidth = Math.max(pwidth, textRenderer.getWidth(text));
        }
        this.promptWidth = pwidth;
        this.closeAndRemoveFunction = closeAndRemoveFunction;
        int buttonWidth = 100;
        RecustomIconButtonWidget[] buttons = this.buttons = new RecustomIconButtonWidget[options.length];
        int buttonX = x + width / 2 - (buttons.length * buttonWidth + (buttons.length - 1) * 4) / 2;
        int buttonOffset = buttonWidth + 4;
        int buttonY = y + height - 30;
        for (int i = 0; i < buttons.length; i++) {
            Option option = options[i];
            RecustomIconButtonWidget button = buttons[i] = new RecustomIconButtonWidget(buttonX, buttonY, buttonWidth, 20, option.text, (btn) -> acceptAndClose(option));
            button.setIcon(option.icon);
            buttonX += buttonOffset;
        }
    }

    private void acceptAndClose(Option option) {
        option.onClicked.run();
        closeAndRemoveFunction.accept(this);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {

    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (ButtonWidget widget : buttons) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        for (ButtonWidget widget : buttons) {
            if (widget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (ButtonWidget widget : buttons) {
            if (widget.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        for (ButtonWidget widget : buttons) {
            widget.mouseMoved(mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (ButtonWidget widget : buttons) {
            if (widget.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (ButtonWidget widget : buttons) {
            if (widget.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        for (ButtonWidget widget : buttons) {
            if (widget.keyReleased(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (ButtonWidget widget : buttons) {
            if (widget.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return true;
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrices = context.getMatrices();
        super.renderButton(context, mouseX, mouseY, delta);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int x1 = getX();
        int x2 = x1 + getWidth();
        int y1 = getY();
        int y2 = getHeight();
        buffer.vertex(x1, y1, 900).color(255, 255, 255, 32).next();
        buffer.vertex(x1, y2, 900).color(255, 255, 255, 32).next();
        buffer.vertex(x2, y2, 900).color(255, 255, 255, 32).next();
        buffer.vertex(x2, y1, 900).color(255, 255, 255, 32).next();
        tess.draw();
        x1 = getX() + width / 2 - promptWidth / 2;
        y1 = getY() + 10;
        matrices.translate(0, 0, 900);
        TextHelper textHelper = TextHelper.get(textRenderer, matrices).immediate(false);
        for (OrderedText text : prompt) {
            textHelper.draw(text, x1, y1);
            y1 += 10;
        }
        textHelper.flushBuffers();
        for (ButtonWidget widget : buttons) {
            widget.render(context, mouseX, mouseY, delta);
        }
    }

    public record Option(String text, Sprite icon, boolean rightAlign, Runnable onClicked) {}
}
