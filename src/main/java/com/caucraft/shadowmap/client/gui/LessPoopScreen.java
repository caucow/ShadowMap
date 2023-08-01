package com.caucraft.shadowmap.client.gui;

import com.caucraft.shadowmap.client.gui.component.ConfirmDialogWidget;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public abstract class LessPoopScreen extends Screen {

    protected LessPoopScreen(Text title) {
        super(title);
    }

    public void confirmAction(String prompt, ConfirmDialogWidget.Option... options) {
        ConfirmDialogWidget dialog = new ConfirmDialogWidget(width / 2 - 140, height / 3 - 40, 280, 80, width, height, client.textRenderer, Text.of(prompt), this::remove, options);
        addDrawableChild(dialog);
        List<Element> children = (List<Element>) children();
        Element last = children.remove(children.size() - 1);
        children.add(0, last);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Why does everything from hovered state to focused element have decouple duplicate functionality
        // Why are there 3 implementations of scrollable "things"
        // Why does this ui system have to be so awful to use
        Optional<Element> hovered = this.hoveredElement(mouseX, mouseY).filter(element -> element.mouseClicked(mouseX, mouseY, button));
        Element hoveredElement = hovered.orElse(null);
        for (Element child : children()) {
            if (hoveredElement != child && child instanceof ClickableWidget && ((ClickableWidget) child).isFocused()) {
                child.changeFocus(true);
            }
        }
        if (hoveredElement == null) {
            return false;
        }
        this.setFocused(hoveredElement);
        if (button == 0) {
            this.setDragging(true);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Element focused = getFocused();
        return super.mouseReleased(mouseX, mouseY, button) | (focused != null && focused.mouseReleased(mouseX, mouseY, button));
    }

    @Override
    public void setFocused(@Nullable Element focused) {
        for (Element child : children()) {
            if (focused != child && child instanceof ClickableWidget && ((ClickableWidget) child).isFocused()) {
                child.changeFocus(true);
            }
        }
        super.setFocused(focused);
    }
}
