package com.caucraft.shadowmap.client.gui.config;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.config.InfoConfig;
import com.caucraft.shadowmap.client.gui.component.RecustomIconButtonWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomToggleButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

public class InfoConfigScreen extends Screen {
    private final Screen parentScreen;
    private final InfoConfig config;
    private final TextWidget titleText;
    private final RecustomToggleButtonWidget enabled;
    private final RecustomToggleButtonWidget showCoords;
    private final RecustomToggleButtonWidget showFacing;
    private final RecustomToggleButtonWidget showLight;
    private final RecustomToggleButtonWidget showWorld;
    private final RecustomToggleButtonWidget showDimension;
    private final RecustomToggleButtonWidget showBiome;
    private final RecustomToggleButtonWidget showWeather;
    private final RecustomToggleButtonWidget showDay;
    private final RecustomToggleButtonWidget showTime;
    private final RecustomIconButtonWidget done;

    public InfoConfigScreen(Screen parentScreen) {
        super(Text.of("HUD Info Settings"));
        this.parentScreen = parentScreen;
        InfoConfig config = this.config = ShadowMap.getInstance().getConfig().infoConfig;
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        this.titleText = new TextWidget(getTitle(), textRenderer);
        this.enabled = new RecustomToggleButtonWidget(0, 0, 150, 20, "Enabled", this::enabledClicked, config.enabled.get());
        this.showCoords = new RecustomToggleButtonWidget(0, 0, 150, 20, "Show Coords", this::showCoordsClicked, config.showCoords.get());
        this.showFacing = new RecustomToggleButtonWidget(0, 0, 150, 20, "Show Facing", this::showFacingClicked, config.showFacing.get());
        this.showLight = new RecustomToggleButtonWidget(0, 0, 150, 20, "Show Light", this::showLightClicked, config.showLight.get());
        this.showWorld = new RecustomToggleButtonWidget(0, 0, 150, 20, "Show World", this::showWorldClicked, config.showWorld.get());
        this.showDimension = new RecustomToggleButtonWidget(0, 0, 150, 20, "Show Dimension", this::showDimensionClicked, config.showDimension.get());
        this.showBiome = new RecustomToggleButtonWidget(0, 0, 150, 20, "Show Biome", this::showBiomeClicked, config.showBiome.get());
        this.showWeather = new RecustomToggleButtonWidget(0, 0, 150, 20, "Show Weather", this::showWeatherClicked, config.showWeather.get());
        this.showDay = new RecustomToggleButtonWidget(0, 0, 150, 20, "Show Day", this::showDayClicked, config.showDay.get());
        this.showTime = new RecustomToggleButtonWidget(0, 0, 150, 20, "Show Time", this::showTimeClicked, config.showTime.get());
        this.done = new RecustomIconButtonWidget(0, 0, 150, 20, "Done", this::doneClicked);
    }

    @Override
    protected void init() {
        addDrawable(titleText);
        addDrawableChild(enabled);
        addDrawableChild(showCoords);
        addDrawableChild(showFacing);
        addDrawableChild(showLight);
        addDrawableChild(showWorld);
        addDrawableChild(showDimension);
        addDrawableChild(showBiome);
        addDrawableChild(showWeather);
        addDrawableChild(showDay);
        addDrawableChild(showTime);
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

        enabled.setPosition(midX - 152, y);
        showCoords.setPosition(midX + 2, y);
        y += 22;

        showFacing.setPosition(midX - 152, y);
        showLight.setPosition(midX + 2, y);
        y += 22;

        showWorld.setPosition(midX - 152, y);
        showDimension.setPosition(midX + 2, y);
        y += 22;

        showBiome.setPosition(midX - 152, y);
        showWeather.setPosition(midX + 2, y);
        y += 22;

        showDay.setPosition(midX - 152, y);
        showTime.setPosition(midX + 2, y);

        y = (height - 240) / 3 + 210;
        done.setPosition(midX - 75, y);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        ShadowMap.getInstance().scheduleSaveConfig();
    }

    private void enabledClicked(ButtonWidget btn) {
        config.enabled.set(enabled.isToggled());
    }

    private void showCoordsClicked(ButtonWidget btn) {
        config.showCoords.set(showCoords.isToggled());
    }

    private void showFacingClicked(ButtonWidget btn) {
        config.showFacing.set(showFacing.isToggled());
    }

    private void showLightClicked(ButtonWidget btn) {
        config.showLight.set(showLight.isToggled());
    }

    private void showWorldClicked(ButtonWidget btn) {
        config.showWorld.set(showWorld.isToggled());
    }

    private void showDimensionClicked(ButtonWidget btn) {
        config.showDimension.set(showDimension.isToggled());
    }

    private void showBiomeClicked(ButtonWidget btn) {
        config.showBiome.set(showBiome.isToggled());
    }

    private void showWeatherClicked(ButtonWidget btn) {
        config.showWeather.set(showWeather.isToggled());
    }

    private void showDayClicked(ButtonWidget btn) {
        config.showDay.set(showDay.isToggled());
    }

    private void showTimeClicked(ButtonWidget btn) {
        config.showTime.set(showTime.isToggled());
    }

    private void doneClicked(ButtonWidget btn) {
        client.setScreen(parentScreen);
    }
}
