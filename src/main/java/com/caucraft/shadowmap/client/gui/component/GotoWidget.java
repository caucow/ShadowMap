package com.caucraft.shadowmap.client.gui.component;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.gui.MapScreen;
import com.caucraft.shadowmap.client.gui.waypoint.WaypointSelectScreen;
import com.caucraft.shadowmap.client.util.TextHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class GotoWidget extends FullscreenWidget {
    private final MapScreen mapScreen;
    private final RecustomIconButtonWidget[] buttons;
    private final RecustomTextFieldWidget[] textFields;
    private final Consumer<GotoWidget> closeAndRemoveFunction;

    public GotoWidget(MapScreen mapScreen, int x, int y, int width, int height, int screenWidth, int screenHeight, Consumer<GotoWidget> closeAndRemoveFunction) {
        super(x, y, width, height, screenWidth, screenHeight, Text.of("Go To..."));
        this.mapScreen = mapScreen;
        this.closeAndRemoveFunction = closeAndRemoveFunction;

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int x1 = getX() + width / 2;
        int y1 = getY() + 10;

        buttons = new RecustomIconButtonWidget[] {
                new RecustomIconButtonWidget(x1 - 50, y1 + 20, 100, 20, "Waypoint", this::waypointClicked),
                new RecustomIconButtonWidget(x1 - 102, y1 + 70, 100, 20, "Go To", this::goToClicked),
                new RecustomIconButtonWidget(x1 + 2, y1 + 70, 100, 20, "Cancel", this::cancelClicked)
        };
        textFields = new RecustomTextFieldWidget[] {
                new RecustomTextFieldWidget(textRenderer, x1 - 100, y1 + 50, 96, 16, null),
                new RecustomTextFieldWidget(textRenderer, x1 + 4, y1 + 50, 96, 16, null)
        };
    }

    private void waypointClicked(ButtonWidget btn) {
        MinecraftClient.getInstance().setScreen(new WaypointSelectScreen(ShadowMap.getInstance(), mapScreen, Text.of("Go To Waypoint"), (waypoint) -> {
            if (waypoint != null) {
                mapScreen.setCenter(waypoint.getPos().x, waypoint.getPos().z);
            }
            MinecraftClient.getInstance().setScreen(mapScreen);
            closeAndRemoveFunction.accept(this);
        }));
    }

    private void goToClicked(ButtonWidget btn) {
        try {
            closeAndRemoveFunction.accept(this);
            mapScreen.setCenter(Integer.parseInt(textFields[0].getText()), Integer.parseInt(textFields[1].getText()));
        } catch (NumberFormatException ignore) {}
    }

    private void cancelClicked(ButtonWidget btn) {
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
                widget.setFocused(true);
            } else {
                widget.setFocused(false);
            }
        }
        for (RecustomTextFieldWidget widget : textFields) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                widget.setFocused(true);
            } else {
                widget.setFocused(false);
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
        for (RecustomTextFieldWidget widget : textFields) {
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
        for (RecustomTextFieldWidget widget : textFields) {
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
        for (RecustomTextFieldWidget widget : textFields) {
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
        for (RecustomTextFieldWidget widget : textFields) {
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
        for (RecustomTextFieldWidget widget : textFields) {
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
        for (RecustomTextFieldWidget widget : textFields) {
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
        for (RecustomTextFieldWidget widget : textFields) {
            if (widget.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return true;
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderButton(context, mouseX, mouseY, delta);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0, 0, 900);
        int x1 = getX() + width / 2;
        int y1 = getY() + 10;
        TextHelper textHelper = TextHelper.get(matrices);
        textHelper.drawCentered(this.getMessage(), x1, y1);
        for (ButtonWidget widget : buttons) {
            widget.render(context, mouseX, mouseY, delta);
        }
        for (RecustomTextFieldWidget widget : textFields) {
            widget.render(context, mouseX, mouseY, delta);
        }
        matrices.pop();
    }
}
