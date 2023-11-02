package com.caucraft.shadowmap.client.gui.config;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.config.MapScreenConfig;
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
import net.minecraft.util.math.MathHelper;

public class MapScreenConfigScreen extends Screen {
    private final Screen parentScreen;
    private final MapScreenConfig config;
    private final TextWidget titleText;
    private final RecustomToggleButtonWidget showGrid;
    private final RecustomToggleButtonWidget showInfo;
    private final RecustomToggleButtonWidget showInfoCoords;
    private final RecustomToggleButtonWidget showInfoBiome;
    private final RecustomToggleButtonWidget showInfoBlocks;
    private final RecustomToggleButtonWidget showInfoBlockstates;
    private final TextWidget minZoomLabel;
    private final RecustomTextFieldWidget minZoom;
    private final TextWidget maxZoomLabel;
    private final RecustomTextFieldWidget maxZoom;
    private final RecustomIconButtonWidget done;

    public MapScreenConfigScreen(Screen parentScreen) {
        super(Text.of("Map Screen Settings"));
        this.parentScreen = parentScreen;
        MapScreenConfig config = this.config = ShadowMap.getInstance().getConfig().mapScreenConfig;
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        this.titleText = new TextWidget(getTitle(), textRenderer);
        this.showGrid = new RecustomToggleButtonWidget(0, 0, 150, 20, "Show Grid", this::showGridClicked, config.showGrid.get());
        this.showInfo = new RecustomToggleButtonWidget(0, 0, 150, 20, "Show Info", this::showInfoClicked, config.showInfo.get());
        this.showInfoCoords = new RecustomToggleButtonWidget(0, 0, 150, 20, "Show Info Coords", this::showInfoCoordsClicked, config.showInfoCoords.get());
        this.showInfoBiome = new RecustomToggleButtonWidget(0, 0, 150, 20, "Show Info Biome", this::showInfoBiomeClicked, config.showInfoBiome.get());
        this.showInfoBlocks = new RecustomToggleButtonWidget(0, 0, 150, 20, "Show Info Blocks", this::showInfoBlocksClicked, config.showInfoBlocks.get());
        this.showInfoBlockstates = new RecustomToggleButtonWidget(0, 0, 150, 20, "Show Info Blockstates", this::showInfoBlockstatesClicked, config.showInfoBlockstates.get());
        this.minZoomLabel = new TextWidget(50, 20, Text.of("Min. Zoom"), textRenderer);
        this.minZoom = new RecustomTextFieldWidget(textRenderer, 0, 0, 96, 16, null);
        this.maxZoomLabel = new TextWidget(50, 20, Text.of("Max. Zoom"), textRenderer);
        this.maxZoom = new RecustomTextFieldWidget(textRenderer, 0, 0, 96, 16, null);
        this.done = new RecustomIconButtonWidget(0, 0, 150, 20, "Done", this::doneClicked);

        this.minZoom.setTextPredicate(RecustomTextFieldWidget.DECIMAL_FILTER);
        this.minZoom.setText(String.format("%.4f", config.minZoom.get()));
        this.maxZoom.setTextPredicate(RecustomTextFieldWidget.DECIMAL_FILTER);
        this.maxZoom.setText(String.format("%.4f", config.maxZoom.get()));
    }

    @Override
    protected void init() {
        addDrawable(titleText);
        addDrawableChild(showGrid);
        addDrawableChild(showInfo);
        addDrawableChild(showInfoCoords);
        addDrawableChild(showInfoBiome);
        addDrawableChild(showInfoBlocks);
        addDrawableChild(showInfoBlockstates);
        addDrawable(minZoomLabel);
        addDrawableChild(minZoom);
        addDrawable(maxZoomLabel);
        addDrawableChild(maxZoom);
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

        showGrid.setPosition(midX - 152, y);
        showInfo.setPosition(midX + 2, y);
        y += 22;

        showInfoCoords.setPosition(midX - 152, y);
        showInfoBiome.setPosition(midX + 2, y);
        y += 22;

        showInfoBlocks.setPosition(midX - 152, y);
        showInfoBlockstates.setPosition(midX + 2, y);
        y += 22;

        minZoomLabel.setPosition(midX - 152, y);
        minZoom.setPosition(midX - 100, y + 2);
        maxZoomLabel.setPosition(midX + 2, y);
        maxZoom.setPosition(midX + 54, y + 2);

        y = (height - 240) / 3 + 210;
        done.setPosition(midX - 75, y);
    }

    @Override
    public void removed() {
        float zoomMax = config.maxZoom.get();
        float zoomMin = config.minZoom.get();
        try {
            zoomMax = Float.parseFloat(this.maxZoom.getText());
        } catch (NumberFormatException ignore) {}
        try {
            zoomMin = Float.parseFloat(this.minZoom.getText());
        } catch (NumberFormatException ignore) {}
        zoomMax = MathHelper.clamp(zoomMax, 0.05F, 64.0F);
        zoomMin = MathHelper.clamp(zoomMin, 0.05F, zoomMax);
        config.minZoom.set(zoomMin);
        config.maxZoom.set(zoomMax);
        ShadowMap.getInstance().scheduleSaveConfig();
    }

    private void showGridClicked(ButtonWidget btn) {
        config.showGrid.set(showGrid.isToggled());
    }

    private void showInfoClicked(ButtonWidget btn) {
        config.showInfo.set(showInfo.isToggled());
    }

    private void showInfoCoordsClicked(ButtonWidget btn) {
        config.showInfoCoords.set(showInfoCoords.isToggled());
    }

    private void showInfoBiomeClicked(ButtonWidget btn) {
        config.showInfoBiome.set(showInfoBiome.isToggled());
    }

    private void showInfoBlocksClicked(ButtonWidget btn) {
        config.showInfoBlocks.set(showInfoBlocks.isToggled());
    }

    private void showInfoBlockstatesClicked(ButtonWidget btn) {
        config.showInfoBlockstates.set(showInfoBlockstates.isToggled());
    }

    private void doneClicked(ButtonWidget btn) {
        client.setScreen(parentScreen);
    }
}
