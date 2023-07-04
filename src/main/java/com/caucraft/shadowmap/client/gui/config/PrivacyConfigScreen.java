package com.caucraft.shadowmap.client.gui.config;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.config.PrivacyConfig;
import com.caucraft.shadowmap.client.gui.component.RecustomIconButtonWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomToggleButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

public class PrivacyConfigScreen extends Screen {
    private final Screen parentScreen;
    private final PrivacyConfig config;
    private final RecustomToggleButtonWidget enabled;
    private final RecustomToggleButtonWidget hideCoords;
    private final RecustomToggleButtonWidget hideFacing;
    private final RecustomToggleButtonWidget hideBiome;
    private final RecustomToggleButtonWidget hideBlocks;
    private final TextWidget titleText;

    private final RecustomIconButtonWidget done;

    public PrivacyConfigScreen(Screen parentScreen) {
        super(Text.of("Privacy Settings"));
        this.parentScreen = parentScreen;
        PrivacyConfig config = this.config = ShadowMap.getInstance().getConfig().privacyConfig;
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        this.titleText = new TextWidget(getTitle(), textRenderer);
        this.enabled = new RecustomToggleButtonWidget(0, 0, 150, 20, "Enable Privacy Mode", this::enabledClicked, config.enablePrivateMode.get());
        this.hideCoords = new RecustomToggleButtonWidget(0, 0, 150, 20, "Hide Coords", this::hideCoordsClicked, config.hideCoords.get());
        this.hideFacing = new RecustomToggleButtonWidget(0, 0, 150, 20, "Hide Facing", this::hideFacingClicked, config.hideFacing.get());
        this.hideBiome = new RecustomToggleButtonWidget(0, 0, 150, 20, "Hide Biome", this::hideBiomeClicked, config.hideBiome.get());
        this.hideBlocks = new RecustomToggleButtonWidget(0, 0, 150, 20, "Hide Blocks", this::hideBlocksClicked, config.hideBlocks.get());
        this.done = new RecustomIconButtonWidget(0, 0, 150, 20, "Done", this::doneClicked);
    }

    @Override
    protected void init() {
        addDrawable(titleText);
        addDrawableChild(enabled);
        addDrawableChild(hideCoords);
        addDrawableChild(hideFacing);
        addDrawableChild(hideBiome);
        addDrawableChild(hideBlocks);
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
        hideCoords.setPosition(midX + 2, y);
        y += 22;

        hideFacing.setPosition(midX - 152, y);
        hideBiome.setPosition(midX + 2, y);
        y += 22;

        hideBlocks.setPosition(midX - 152, y);

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
        config.enablePrivateMode.set(enabled.isToggled());
    }

    private void hideCoordsClicked(ButtonWidget btn) {
        config.hideCoords.set(hideCoords.isToggled());
    }

    private void hideFacingClicked(ButtonWidget btn) {
        config.hideFacing.set(hideFacing.isToggled());
    }

    private void hideBiomeClicked(ButtonWidget btn) {
        config.hideBiome.set(hideBiome.isToggled());
    }

    private void hideBlocksClicked(ButtonWidget btn) {
        config.hideBlocks.set(hideBlocks.isToggled());
    }

    private void doneClicked(ButtonWidget btn) {
        client.setScreen(parentScreen);
    }
}
