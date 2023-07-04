package com.caucraft.shadowmap.client.gui.component;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecustomTextFieldWidget extends TextFieldWidget {
    public static final Predicate<String> INTEGER_FILTER;
    public static final Predicate<String> DECIMAL_FILTER;
    public static final Predicate<String> HEX_RGB_FILTER;
    public static final Predicate<String> HEX_ARGB_FILTER;

    static {
        {
            Pattern integerPattern = Pattern.compile("[-+]?\\d+");
            INTEGER_FILTER = (str) -> {
                Matcher m = integerPattern.matcher(str);
                return m.matches() || m.hitEnd();
            };
        }
        {
            Pattern decimalPattern = Pattern.compile("[\\-+]?\\d*\\.?\\d*(e\\d+)?");
            DECIMAL_FILTER = (str) -> {
                Matcher m = decimalPattern.matcher(str);
                return m.matches() || m.hitEnd();
            };
        }
        {
            Pattern rgbPattern = Pattern.compile("[0-9a-fA-F]{6}");
            HEX_RGB_FILTER = (str) -> {
                Matcher m = rgbPattern.matcher(str);
                return m.matches() || m.hitEnd();
            };
        }
        {
            Pattern argbPattern = Pattern.compile("[0-9a-fA-F]{8}");
            HEX_ARGB_FILTER = (str) -> {
                Matcher m = argbPattern.matcher(str);
                return m.matches() || m.hitEnd();
            };
        }
    }

    private boolean censored;
    private String suggestionHint;
    private Consumer<String> onTypedChange;

    public RecustomTextFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text text) {
        super(textRenderer, x, y, width, height, text);
        setChangedListener(null);
    }

    public RecustomTextFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height,
            @Nullable TextFieldWidget copyFrom, Text text) {
        super(textRenderer, x, y, width, height, copyFrom, text);
    }

    public void setTypedChangeListener(Consumer<String> onTypedChange) {
        this.onTypedChange = onTypedChange;
    }

    @Override
    public void setText(String text) {
        super.setText(text == null ? "" : text);
    }

    @Override
    public void write(String text) {
        super.write(text);
        if (onTypedChange != null) {
            onTypedChange.accept(getText());
        }
    }

    @Override
    public void eraseCharacters(int characterOffset) {
        super.eraseCharacters(characterOffset);
        if (onTypedChange != null) {
            onTypedChange.accept(getText());
        }
    }

    @Override
    public void eraseWords(int wordOffset) {
        super.eraseWords(wordOffset);
        if (onTypedChange != null) {
            onTypedChange.accept(getText());
        }
    }

    public boolean isCensored() {
        return censored;
    }

    public void setCensored(boolean censored) {
        this.censored = censored;
    }

    public String getSuggestionHint() {
        return suggestionHint;
    }

    public void setSuggestionHint(String suggestionHint) {
        this.suggestionHint = suggestionHint;
        if (getText().isEmpty()) {
            setSuggestion(suggestionHint);
        }
    }

    @Override
    public void setCursor(int cursor) {
        if (cursor > 0) {
            super.setCursor(Math.max(0, cursor - 2));
        }
        super.setCursor(cursor);
    }

    @Override
    public void setChangedListener(Consumer<String> changedListener) {
        super.setChangedListener((str) -> {
            if (str.isEmpty()) {
                setSuggestion(getSuggestionHint());
            } else {
                setSuggestion(null);
            }
            if (changedListener != null) {
                changedListener.accept(str);
            }
        });
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderSystem.enableDepthTest();
        super.renderButton(context, mouseX, mouseY, delta);
    }
}
