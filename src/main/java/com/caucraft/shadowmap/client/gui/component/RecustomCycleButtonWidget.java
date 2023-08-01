package com.caucraft.shadowmap.client.gui.component;

import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.MathHelper;

public class RecustomCycleButtonWidget<T> extends RecustomIconButtonWidget {

    private T[] tArray;
    private int currentIndex;
    private Sprite[] icons;

    public RecustomCycleButtonWidget(int x, int y, int width, int height, String message, PressAction onPress, T[] tArray, int currentIndex, Sprite[] icons) {
        super(x, y, width, height, message, onPress);
        this.tArray = tArray;
        this.currentIndex = currentIndex;
        this.icons = icons;
        if (icons != null) {
            setIcon(icons[currentIndex]);
        }
    }

    public void setCurrentValueIndex(int index) {
        index = MathHelper.clamp(index, 0, tArray.length - 1);
        this.currentIndex = index;
        if (icons != null) {
            setIcon(icons[index]);
        }
    }

    public int getCurrentValueIndex() {
        return currentIndex;
    }

    public T getCurrentValue() {
        return tArray[currentIndex];
    }

    @Override
    public void onPress() {
        setCurrentValueIndex((currentIndex + 1) % tArray.length);
        super.onPress();
    }

    @Override
    public String getDisplayText() {
        return super.getDisplayText() + ": " + getCurrentValue().toString();
    }
}
