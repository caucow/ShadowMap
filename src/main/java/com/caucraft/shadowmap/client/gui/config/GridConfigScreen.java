package com.caucraft.shadowmap.client.gui.config;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.config.GridConfig;
import com.caucraft.shadowmap.client.gui.component.RecustomIconButtonWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomTextFieldWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomToggleButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

public class GridConfigScreen extends Screen {
    private final Screen parentScreen;
    private final GridConfig config;
    private final TextWidget titleText;
    private final RecustomToggleButtonWidget showGridChunk;
    private final TextWidget gridColorChunkLabel;
    private final RecustomTextFieldWidget gridColorChunk;
    private final RecustomToggleButtonWidget showGridRegions;
    private final TextWidget gridColorRegionLabel;
    private final RecustomTextFieldWidget gridColorRegion;
    private final TextWidget gridColorRegion32Label;
    private final RecustomTextFieldWidget gridColorRegion32;
    private final RecustomIconButtonWidget done;

    public GridConfigScreen(Screen parentScreen) {
        super(Text.of("Chunk Grid Settings"));
        this.parentScreen = parentScreen;
        GridConfig config = this.config = ShadowMap.getInstance().getConfig().gridConfig;
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        this.titleText = new TextWidget(getTitle(), textRenderer);
        this.showGridChunk = new RecustomToggleButtonWidget(0, 0, 150, 20, "Chunk Grid", this::showGridChunkClicked, config.showGridChunks.get());
        this.gridColorChunkLabel = new TextWidget(80, 20, Text.of("Chunk Color"), textRenderer);
        this.gridColorChunk = new RecustomTextFieldWidget(textRenderer, 0, 0, 66, 16, null);
        this.gridColorChunk.setTypedChangeListener(this::gridColorChunkChanged);
        this.showGridRegions = new RecustomToggleButtonWidget(0, 0, 150, 20, "Region Grid", this::showGridRegionClicked, config.showGridRegions.get());
        this.gridColorRegionLabel = new TextWidget(80, 20, Text.of("Region Color"), textRenderer);
        this.gridColorRegion = new RecustomTextFieldWidget(textRenderer, 0, 0, 66, 16, null);
        this.gridColorRegion.setTypedChangeListener(this::gridColorRegionChanged);
        this.gridColorRegion32Label = new TextWidget(80, 20, Text.of("Region32 Color"), textRenderer);
        this.gridColorRegion32 = new RecustomTextFieldWidget(textRenderer, 0, 0, 66, 16, null);
        this.gridColorRegion32.setTypedChangeListener(this::gridColorRegion32Changed);
        this.done = new RecustomIconButtonWidget(0, 0, 150, 20, "Done", this::doneClicked);

        this.gridColorChunk.setTextPredicate(RecustomTextFieldWidget.HEX_ARGB_FILTER);
        this.gridColorChunk.setText(String.format("%08x", config.gridColorChunk.get()));
        this.gridColorRegion.setTextPredicate(RecustomTextFieldWidget.HEX_ARGB_FILTER);
        this.gridColorRegion.setText(String.format("%08x", config.gridColorRegion.get()));
        this.gridColorRegion32.setTextPredicate(RecustomTextFieldWidget.HEX_ARGB_FILTER);
        this.gridColorRegion32.setText(String.format("%08x", config.gridColorRegion32.get()));
    }

    @Override
    protected void init() {
        addDrawable(titleText);
        addDrawableChild(showGridChunk);
        addDrawable(gridColorChunkLabel);
        addDrawableChild(gridColorChunk);
        addDrawableChild(showGridRegions);
        addDrawable(gridColorRegionLabel);
        addDrawableChild(gridColorRegion);
        addDrawable(gridColorRegion32Label);
        addDrawableChild(gridColorRegion32);
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

        showGridChunk.setPosition(midX - 152, y);
        gridColorChunkLabel.setPosition(midX + 2, y);
        gridColorChunk.setPosition(midX + 84, y + 2);
        y += 22;

        showGridRegions.setPosition(midX - 152, y);
        gridColorRegionLabel.setPosition(midX + 2, y);
        gridColorRegion.setPosition(midX + 84, y + 2);
        y += 22;

        gridColorRegion32Label.setPosition(midX + 2, y);
        gridColorRegion32.setPosition(midX + 84, y + 2);

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

    private void showGridChunkClicked(ButtonWidget btn) {
        config.showGridChunks.set(showGridChunk.isToggled());
    }

    private void gridColorChunkChanged(String text) {
        try {
            config.gridColorChunk.set((int) Long.parseLong(text, 16));
        } catch (NumberFormatException ignore) {}
    }

    private void showGridRegionClicked(ButtonWidget btn) {
        config.showGridRegions.set(showGridRegions.isToggled());
    }

    private void gridColorRegionChanged(String text) {
        try {
            config.gridColorRegion.set((int) Long.parseLong(text, 16));
        } catch (NumberFormatException ignore) {}
    }

    private void gridColorRegion32Changed(String text) {
        try {
            config.gridColorRegion32.set((int) Long.parseLong(text, 16));
        } catch (NumberFormatException ignore) {}
    }

    private void doneClicked(ButtonWidget btn) {
        client.setScreen(parentScreen);
    }
}
