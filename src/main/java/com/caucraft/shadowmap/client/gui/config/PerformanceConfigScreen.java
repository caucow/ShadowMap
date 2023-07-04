package com.caucraft.shadowmap.client.gui.config;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.config.PerformanceConfig;
import com.caucraft.shadowmap.client.gui.component.RecustomCycleButtonWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomIconButtonWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomTextFieldWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

public class PerformanceConfigScreen extends Screen {
    private final Screen parentScreen;
    private final PerformanceConfig config;
    private final TextWidget titleText;
    private final RecustomCycleButtonWidget<PerformanceConfig.PerformanceMode> performanceMode;
    private final TextWidget blockMemoryLabel;
    private final RecustomTextFieldWidget blockMemory;
    private final TextWidget blockTimeoutLabel;
    private final RecustomTextFieldWidget blockTimeout;
    private final TextWidget metaMemoryLabel;
    private final RecustomTextFieldWidget metaMemory;
    private final TextWidget metaTimeoutLabel;
    private final RecustomTextFieldWidget metaTimeout;
    private final TextWidget textureMemoryLabel;
    private final RecustomTextFieldWidget textureMemory;
    private final TextWidget textureTimeoutLabel;
    private final RecustomTextFieldWidget textureTimeout;
    private final RecustomIconButtonWidget done;

    public PerformanceConfigScreen(Screen parentScreen) {
        super(Text.of("Performance Settings"));
        this.parentScreen = parentScreen;
        PerformanceConfig config = this.config = ShadowMap.getInstance().getConfig().performanceConfig;
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        this.titleText = new TextWidget(getTitle(), textRenderer);
        this.performanceMode = new RecustomCycleButtonWidget<>(0, 0, 150, 20, "Performance Preset", this::performanceModeClicked, PerformanceConfig.PerformanceMode.values(), config.performanceMode.get().ordinal(), null);
        this.blockMemoryLabel = new TextWidget(100, 20, Text.of("Blocks Memory (MB)"), textRenderer);
        this.blockMemory = new RecustomTextFieldWidget(textRenderer, 0, 0, 46, 16, null);
        this.blockMemory.setTypedChangeListener(this::blockMemoryChanged);
        this.blockTimeoutLabel = new TextWidget(100, 20, Text.of("Blocks Timeout (s)"), textRenderer);
        this.blockTimeout = new RecustomTextFieldWidget(textRenderer, 0, 0, 46, 16, null);
        this.blockTimeout.setTypedChangeListener(this::blockTimeoutChanged);
        this.metaMemoryLabel = new TextWidget(100, 20, Text.of("Meta Memory (MB)"), textRenderer);
        this.metaMemory = new RecustomTextFieldWidget(textRenderer, 0, 0, 46, 16, null);
        this.metaMemory.setTypedChangeListener(this::metaMemoryChanged);
        this.metaTimeoutLabel = new TextWidget(100, 20, Text.of("Meta Timeout (s)"), textRenderer);
        this.metaTimeout = new RecustomTextFieldWidget(textRenderer, 0, 0, 46, 16, null);
        this.metaTimeout.setTypedChangeListener(this::metaTimeoutChanged);
        this.textureMemoryLabel = new TextWidget(100, 20, Text.of("Texture Memory (MB)"), textRenderer);
        this.textureMemory = new RecustomTextFieldWidget(textRenderer, 0, 0, 46, 16, null);
        this.textureMemory.setTypedChangeListener(this::textureMemoryChanged);
        this.textureTimeoutLabel = new TextWidget(100, 20, Text.of("Texture Timeout (s)"), textRenderer);
        this.textureTimeout = new RecustomTextFieldWidget(textRenderer, 0, 0, 46, 16, null);
        this.textureTimeout.setTypedChangeListener(this::textureTimeoutChanged);
        this.done = new RecustomIconButtonWidget(0, 0, 150, 20, "Done", this::doneClicked);

        this.blockMemory.setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
        this.blockMemory.setText(Integer.toString(config.blockMemoryMB.get()));
        this.blockTimeout.setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
        this.blockTimeout.setText(Integer.toString(config.blockTimeoutS.get()));

        this.metaMemory.setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
        this.metaMemory.setText(Integer.toString(config.metaMemoryMB.get()));
        this.metaTimeout.setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
        this.metaTimeout.setText(Integer.toString(config.metaTimeoutS.get()));

        this.textureMemory.setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
        this.textureMemory.setText(Integer.toString(config.textureMemoryMB.get()));
        this.textureTimeout.setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
        this.textureTimeout.setText(Integer.toString(config.textureTimeoutS.get()));

    }

    @Override
    protected void init() {
        addDrawable(titleText);
        addDrawable(blockMemoryLabel);
        addDrawableChild(blockMemory);
        addDrawable(blockTimeoutLabel);
        addDrawableChild(blockTimeout);
        addDrawable(metaMemoryLabel);
        addDrawableChild(metaMemory);
        addDrawable(metaTimeoutLabel);
        addDrawableChild(metaTimeout);
        addDrawable(textureMemoryLabel);
        addDrawableChild(textureMemory);
        addDrawable(textureTimeoutLabel);
        addDrawableChild(textureTimeout);
        addDrawableChild(done);

        resize(client, width, height);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        this.width = width;
        this.height = height;

        final int midX = width / 2;
        int x = midX - (titleText.getWidth()) / 2;
        int y = (height - 240) / 3 + 10;
        titleText.setPosition(x, y);
        y += 16;

        blockMemoryLabel.setPosition(midX - 152, y);
        blockMemory.setPosition(midX - 50, y + 2);
        blockTimeoutLabel.setPosition(midX + 2, y);
        blockTimeout.setPosition(midX + 104, y + 2);
        y += 22;

        metaMemoryLabel.setPosition(midX - 152, y);
        metaMemory.setPosition(midX - 50, y + 2);
        metaTimeoutLabel.setPosition(midX + 2, y);
        metaTimeout.setPosition(midX + 104, y + 2);
        y += 22;

        textureMemoryLabel.setPosition(midX - 152, y);
        textureMemory.setPosition(midX - 50, y + 2);
        textureTimeoutLabel.setPosition(midX + 2, y);
        textureTimeout.setPosition(midX + 104, y + 2);
        y += 22;

        y = (height - 240) / 3 + 210;
        done.setPosition(midX - 75, y);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        ShadowMap.getInstance().scheduleSaveConfig();
    }

    private void performanceModeClicked(ButtonWidget btn) {
        config.performanceMode.set(performanceMode.getCurrentValue());
    }

    private void blockMemoryChanged(String text) {
        try {
            config.blockMemoryMB.set(Integer.parseInt(text));
        } catch (NumberFormatException ignore) {}
    }

    private void blockTimeoutChanged(String text) {
        try {
            config.blockTimeoutS.set(Integer.parseInt(text));
        } catch (NumberFormatException ignore) {}
    }

    private void metaMemoryChanged(String text) {
        try {
            config.metaMemoryMB.set(Integer.parseInt(text));
        } catch (NumberFormatException ignore) {}
    }

    private void metaTimeoutChanged(String text) {
        try {
            config.metaTimeoutS.set(Integer.parseInt(text));
        } catch (NumberFormatException ignore) {}
    }

    private void textureMemoryChanged(String text) {
        try {
            config.textureMemoryMB.set(Integer.parseInt(text));
        } catch (NumberFormatException ignore) {}
    }

    private void textureTimeoutChanged(String text) {
        try {
            config.textureTimeoutS.set(Integer.parseInt(text));
        } catch (NumberFormatException ignore) {}
    }

    private void doneClicked(ButtonWidget btn) {
        client.setScreen(parentScreen);
    }
}
