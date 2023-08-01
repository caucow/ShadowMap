package com.caucraft.shadowmap.client.gui.component;

import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.texture.Sprite;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public class RecustomToggleButtonWidget extends RecustomIconButtonWidget {
    private boolean toggled;
    private Sprite disabledSprite;
    private Sprite enabledSprite;

    public RecustomToggleButtonWidget(int x, int y, int width, int height, String message, PressAction onPress, boolean toggled) {
        super(x, y, width, height, message, onPress);
        this.toggled = toggled;
    }

    public boolean isToggled() {
        return toggled;
    }

    public void setToggled(boolean toggled) {
        this.toggled = toggled;
    }

    public void setIcons(Sprite disabled, Sprite enabled) {
        this.disabledSprite = disabled;
        this.enabledSprite = enabled;
        setIcon(toggled ? enabled : disabled);
    }

    @Override
    public String getDisplayText() {
        return super.getDisplayText() + (toggled ? ": true" : ": false");
    }

    @Override
    protected MutableText getNarrationMessage() {
        return ClickableWidget.getNarrationMessage(Text.of(getText() + (toggled ? " enabled" : " disabled")));
    }

    @Override
    public void onPress() {
        toggled = !toggled;
        setIcon(toggled ? enabledSprite : disabledSprite);
        super.onPress();
    }
}
