package com.caucraft.shadowmap.client.gui.config;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.config.MinimapConfig;
import com.caucraft.shadowmap.client.gui.component.RecustomCycleButtonWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomIconButtonWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomTextFieldWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomToggleButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class MinimapConfigScreen extends Screen {
    private final Screen parentScreen;
    private final MinimapConfig config;
    private final TextWidget titleText;
    private final RecustomToggleButtonWidget enabled;
    private final RecustomToggleButtonWidget showGrid;
    private final RecustomCycleButtonWidget<MinimapConfig.Shape> shape;
    private final RecustomToggleButtonWidget lockNorth;
    private final RecustomCycleButtonWidget<MinimapConfig.HorizontalAlignment> horizontalAlignment;
    private final RecustomCycleButtonWidget<MinimapConfig.VerticalAlignment> verticalAlignment;
    private final TextWidget offsetXLabel;
    private final RecustomTextFieldWidget offsetX;
    private final TextWidget offsetYLabel;
    private final RecustomTextFieldWidget offsetY;
    private final RecustomCycleButtonWidget<MinimapConfig.SizeMode> sizeMode;
    private final TextWidget radiusLabel;
    private final RecustomTextFieldWidget radius;
    private final TextWidget uiScaleMaxLabel;
    private final RecustomTextFieldWidget uiScaleMax;
    private final TextWidget zoomLabel;
    private final RecustomTextFieldWidget zoom;
    private final RecustomToggleButtonWidget zoomWrap;
    private final TextWidget minZoomLabel;
    private final RecustomTextFieldWidget minZoom;
    private final TextWidget maxZoomLabel;
    private final RecustomTextFieldWidget maxZoom;
    private final RecustomToggleButtonWidget showCompass;
    private final TextWidget compassUiScaleMaxLabel;
    private final RecustomTextFieldWidget compassUiScaleMax;
    private final RecustomIconButtonWidget done;

    public MinimapConfigScreen(Screen parentScreen) {
        super(Text.of("Minimap Settings"));
        this.parentScreen = parentScreen;
        MinimapConfig config = this.config = ShadowMap.getInstance().getConfig().minimapConfig;
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        this.titleText = new TextWidget(getTitle(), textRenderer);

        this.enabled = new RecustomToggleButtonWidget(0, 0, 150, 20, "Minimap Enabled", this::enabledClicked, config.enabled.get());
        this.showGrid = new RecustomToggleButtonWidget(0, 0, 150, 20, "Show Grid", this::showGridClicked, config.showGrid.get());
        this.shape = new RecustomCycleButtonWidget<>(0, 0, 150, 20, "Shape", this::shapeClicked, MinimapConfig.Shape.values(), config.shape.get().ordinal(), null);
        this.lockNorth = new RecustomToggleButtonWidget(0, 0, 150, 20, "Lock North", this::lockNorthClicked, config.lockNorth.get());
        this.horizontalAlignment = new RecustomCycleButtonWidget<>(0, 0, 150, 20, "Horizontal Alignment", this::horizontalAlignmentClicked, MinimapConfig.HorizontalAlignment.values(), config.horizontalAlignment.get().ordinal(), null);
        this.verticalAlignment = new RecustomCycleButtonWidget<>(0, 0, 150, 20, "Vertical Alignment", this::verticalAlignmentClicked, MinimapConfig.VerticalAlignment.values(), config.verticalAlignment.get().ordinal(), null);
        this.offsetXLabel = new TextWidget(100, 20, Text.of("X Offset"), textRenderer);
        this.offsetX = new RecustomTextFieldWidget(textRenderer, 0, 0, 46, 16, null);
        this.offsetX.setTypedChangeListener(this::offsetXChanged);
        this.offsetYLabel = new TextWidget(100, 20, Text.of("Y Offset"), textRenderer);
        this.offsetY = new RecustomTextFieldWidget(textRenderer, 0, 0, 46, 16, null);
        this.offsetY.setTypedChangeListener(this::offsetYChanged);
        this.sizeMode = new RecustomCycleButtonWidget<>(0, 0, 150, 20, "Radius Type", this::sizeModeClicked, MinimapConfig.SizeMode.values(), config.sizeMode.get().ordinal(), null);
        this.radiusLabel = new TextWidget(100, 20, Text.of("Radius"), textRenderer);
        this.radius = new RecustomTextFieldWidget(textRenderer, 0, 0, 46, 16, null);
        this.radius.setTypedChangeListener(this::radiusChanged);
        this.uiScaleMaxLabel = new TextWidget(100, 20, Text.of("Max. UI Scale"), textRenderer);
        this.uiScaleMax = new RecustomTextFieldWidget(textRenderer, 0, 0, 46, 16, null);
        this.uiScaleMax.setTypedChangeListener(this::scaleMaxChanged);
        this.zoomLabel = new TextWidget(50, 20, Text.of("Zoom"), textRenderer);
        this.zoom = new RecustomTextFieldWidget(textRenderer, 0, 0, 96, 16, null);
        this.zoomWrap = new RecustomToggleButtonWidget(0, 0, 150, 20, "Zoom Wrap", this::zoomWrapClicked, config.zoomWrap.get());
        this.minZoomLabel = new TextWidget(50, 20, Text.of("Min. Zoom"), textRenderer);
        this.minZoom = new RecustomTextFieldWidget(textRenderer, 0, 0, 96, 16, null);
        this.maxZoomLabel = new TextWidget(50, 20, Text.of("Max. Zoom"), textRenderer);
        this.maxZoom = new RecustomTextFieldWidget(textRenderer, 0, 0, 96, 16, null);
        this.showCompass = new RecustomToggleButtonWidget(0, 0, 150, 20, "Show Compass", this::showCompassClicked, config.showCompass.get());
        this.compassUiScaleMaxLabel = new TextWidget(100, 20, Text.of("Max. Compass Scale"), textRenderer);
        this.compassUiScaleMax = new RecustomTextFieldWidget(textRenderer, 0, 0, 46, 16, null);
        this.compassUiScaleMax.setTypedChangeListener(this::compassScaleChanged);
        this.done = new RecustomIconButtonWidget(0, 0, 150, 20, "Done", this::doneClicked);

        this.offsetX.setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
        this.offsetX.setText(Integer.toString(config.offsetX.get()));
        this.offsetY.setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
        this.offsetY.setText(Integer.toString(config.offsetY.get()));
        this.radius.setTextPredicate(switch (sizeMode.getCurrentValue()) {
            case ABSOLUTE -> RecustomTextFieldWidget.INTEGER_FILTER;
            case PERCENT -> RecustomTextFieldWidget.DECIMAL_FILTER;
        });
        this.radius.setText(switch (sizeMode.getCurrentValue()) {
            case ABSOLUTE -> Integer.toString(config.radiusAbsolute.get());
            case PERCENT -> Float.toString(100.0F * config.radiusPercent.get());
        });
        this.uiScaleMax.setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
        this.uiScaleMax.setText(Integer.toString(config.uiScaleMax.get()));
        this.zoom.setTextPredicate(RecustomTextFieldWidget.DECIMAL_FILTER);
        this.zoom.setText(String.format("%.4f", config.zoom.get()));
        this.minZoom.setTextPredicate(RecustomTextFieldWidget.DECIMAL_FILTER);
        this.minZoom.setText(String.format("%.4f", config.minZoom.get()));
        this.maxZoom.setTextPredicate(RecustomTextFieldWidget.DECIMAL_FILTER);
        this.maxZoom.setText(String.format("%.4f", config.maxZoom.get()));
        this.compassUiScaleMax.setTextPredicate(RecustomTextFieldWidget.DECIMAL_FILTER);
        this.compassUiScaleMax.setText(Integer.toString(config.compassUiScaleMax.get()));
    }

    @Override
    protected void init() {
        addDrawableChild(titleText);
        addDrawableChild(enabled);
        addDrawableChild(showGrid);
        addDrawableChild(shape);
        addDrawableChild(lockNorth);
        addDrawableChild(horizontalAlignment);
        addDrawableChild(verticalAlignment);
        addDrawable(offsetXLabel);
        addDrawableChild(offsetX);
        addDrawable(offsetYLabel);
        addDrawableChild(offsetY);
        addDrawableChild(sizeMode);
        addDrawable(radiusLabel);
        addDrawableChild(radius);
        addDrawable(uiScaleMaxLabel);
        addDrawableChild(uiScaleMax);
        addDrawable(zoomLabel);
        addDrawableChild(zoom);
        addDrawableChild(zoomWrap);
        addDrawable(minZoomLabel);
        addDrawableChild(minZoom);
        addDrawable(maxZoomLabel);
        addDrawableChild(maxZoom);
        addDrawableChild(showCompass);
        addDrawable(compassUiScaleMaxLabel);
        addDrawableChild(compassUiScaleMax);
        addDrawableChild(done);

        resize(client, width, height);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        this.width = width;
        this.height = height;

        final int midX = width / 2;
        int x = midX - (titleText.getWidth()) / 2;
        int y = (height - 240) / 3 + 5;
        titleText.setPos(x, y);
        y += 10;

        enabled.setPos(midX - 152, y);
        showGrid.setPos(midX + 2, y);
        y += 22;

        shape.setPos(midX - 152, y);
        lockNorth.setPos(midX + 2, y);
        y += 22;

        horizontalAlignment.setPos(midX - 152, y);
        offsetXLabel.setPos(midX + 2, y);
        offsetX.setPos(midX + 104, y + 2);
        y += 22;

        verticalAlignment.setPos(midX - 152, y);
        offsetYLabel.setPos(midX + 2, y);
        offsetY.setPos(midX + 104, y + 2);
        y += 22;

        sizeMode.setPos(midX - 152, y);
        radiusLabel.setPos(midX + 2, y);
        radius.setPos(midX + 104, y + 2);
        y += 22;

        zoomLabel.setPos(midX - 152, y);
        zoom.setPos(midX - 100, y + 2);
        zoomWrap.setPos(midX + 2, y);
        y += 22;

        minZoomLabel.setPos(midX - 152, y);
        minZoom.setPos(midX - 100, y + 2);
        maxZoomLabel.setPos(midX + 2, y);
        maxZoom.setPos(midX + 54, y + 2);
        y += 22;

        showCompass.setPos(midX - 152, y);
        compassUiScaleMaxLabel.setPos(midX + 2, y);
        compassUiScaleMax.setPos(midX + 104, y + 2);
        y += 22;

        uiScaleMaxLabel.setPos(midX + 2, y);
        uiScaleMax.setPos(midX + 104, y + 2);
        y += 22;

        done.setPos(midX - 75, y);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        float zoomMax = config.maxZoom.get();
        float zoomMin = config.minZoom.get();
        float zoom = config.zoom.get();
        try {
            zoomMax = Float.parseFloat(this.maxZoom.getText());
        } catch (NumberFormatException ignore) {}
        try {
            zoomMin = Float.parseFloat(this.minZoom.getText());
        } catch (NumberFormatException ignore) {}
        try {
            zoom = Float.parseFloat(this.zoom.getText());
        } catch (NumberFormatException ignore) {}
        zoomMax = MathHelper.clamp(zoomMax, 0.05F, 64.0F);
        zoomMin = MathHelper.clamp(zoomMin, 0.05F, zoomMax);
        zoom = MathHelper.clamp(zoom, zoomMin, zoomMax);
        config.minZoom.set(zoomMin);
        config.maxZoom.set(zoomMax);
        config.zoom.set(zoom);
        ShadowMap.getInstance().scheduleSaveConfig();
    }

    private void enabledClicked(ButtonWidget btn) {
        config.enabled.set(enabled.isToggled());
    }

    private void shapeClicked(ButtonWidget btn) {
        config.shape.set(shape.getCurrentValue());
    }

    private void lockNorthClicked(ButtonWidget btn) {
        config.lockNorth.set(lockNorth.isToggled());
    }

    private void horizontalAlignmentClicked(ButtonWidget btn) {
        config.horizontalAlignment.set(horizontalAlignment.getCurrentValue());
    }

    private void verticalAlignmentClicked(ButtonWidget btn) {
        config.verticalAlignment.set(verticalAlignment.getCurrentValue());
    }

    private void offsetXChanged(String text) {
        try {
            config.offsetX.set(Integer.parseInt(text));
        } catch (NumberFormatException ignore) {}
    }

    private void offsetYChanged(String text) {
        try {
            config.offsetY.set(Integer.parseInt(text));
        } catch (NumberFormatException ignore) {}
    }

    private void sizeModeClicked(ButtonWidget btn) {
        config.sizeMode.set(sizeMode.getCurrentValue());
        this.radius.setTextPredicate(switch (sizeMode.getCurrentValue()) {
            case ABSOLUTE -> RecustomTextFieldWidget.INTEGER_FILTER;
            case PERCENT -> RecustomTextFieldWidget.DECIMAL_FILTER;
        });
        this.radius.setText(switch (sizeMode.getCurrentValue()) {
            case ABSOLUTE -> Integer.toString(config.radiusAbsolute.get());
            case PERCENT -> Float.toString(100.0F * config.radiusPercent.get());
        });
    }

    private void radiusChanged(String text) {
        try {
            switch (sizeMode.getCurrentValue()) {
                case ABSOLUTE -> config.radiusAbsolute.set(Integer.parseInt(text));
                case PERCENT -> config.radiusPercent.set(0.01F * Float.parseFloat(text));
            }
        } catch (NumberFormatException ignore) {}
    }

    private void scaleMaxChanged(String text) {
        try {
            config.uiScaleMax.set(Integer.parseInt(text));
        } catch (NumberFormatException ignore) {}
    }

    private void zoomWrapClicked(ButtonWidget btn) {
        config.zoomWrap.set(zoomWrap.isToggled());
    }

    private void showCompassClicked(ButtonWidget btn) {
        config.showCompass.set(showCompass.isToggled());
    }

    private void compassScaleChanged(String text) {
        try {
            config.compassUiScaleMax.set(Integer.parseInt(text));
        } catch (NumberFormatException ignore) {}
    }

    private void showGridClicked(ButtonWidget btn) {
        config.showGrid.set(showGrid.isToggled());
    }

    private void doneClicked(ButtonWidget btn) {
        client.setScreen(parentScreen);
    }
}
