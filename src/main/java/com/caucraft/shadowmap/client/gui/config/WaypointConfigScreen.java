package com.caucraft.shadowmap.client.gui.config;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.config.WaypointConfig;
import com.caucraft.shadowmap.client.gui.component.RecustomCycleButtonWidget;
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

public class WaypointConfigScreen extends Screen {
    private final Screen parentScreen;
    private final WaypointConfig config;
    private final TextWidget titleText;
    private final RecustomToggleButtonWidget showOnMinimap;
    private final RecustomToggleButtonWidget showOnMapScreen;
    private final RecustomToggleButtonWidget showInWorld;
    private final RecustomCycleButtonWidget<WaypointConfig.Shape> shape;
    private final TextWidget pointSizeLabel;
    private final RecustomTextFieldWidget pointSize;
    private final TextWidget maxMapUiScaleLabel;
    private final RecustomTextFieldWidget maxMapUiScale;
    private final TextWidget maxWorldUiScaleLabel;
    private final RecustomTextFieldWidget maxWorldUiScale;
    private final RecustomToggleButtonWidget hideCoords;
    private final TextWidget defaultVisibleDistanceLabel;
    private final RecustomTextFieldWidget defaultVisibleDistance;
    private final TextWidget defaultExpandDistanceLabel;
    private final RecustomTextFieldWidget defaultExpandDistance;
    private final RecustomToggleButtonWidget deathWaypoints;
    private final TextWidget defaultDeathGroupNameLabel;
    private final RecustomTextFieldWidget defaultDeathGroupName;
    private final TextWidget defaultDeathPointNameLabel;
    private final RecustomTextFieldWidget defaultDeathPointName;
    private final RecustomToggleButtonWidget showDistanceInWorld;
    private final RecustomToggleButtonWidget showDistanceOnMap;
    private final RecustomToggleButtonWidget ignoreVisibleFilterOnMapScreen;
    private final RecustomToggleButtonWidget ignoreExpandFilterOnMapScreen;
    private final RecustomIconButtonWidget done;

    public WaypointConfigScreen(Screen parentScreen) {
        super(Text.of("Waypoint Settings"));
        this.parentScreen = parentScreen;
        WaypointConfig config = this.config = ShadowMap.getInstance().getConfig().waypointConfig;
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        this.titleText = new TextWidget(getTitle(), textRenderer);
        this.showOnMinimap = new RecustomToggleButtonWidget(0, 0, 150, 20, "Visible on Minimap", this::showOnMinimapClicked, config.showOnMinimap.get());
        this.showOnMapScreen = new RecustomToggleButtonWidget(0, 0, 150, 20, "Visible on Map Screen", this::showOnMapScreenClicked, config.showOnMapScreen.get());
        this.showInWorld = new RecustomToggleButtonWidget(0, 0, 150, 20, "Visible in World", this::showInWorldClicked, config.showInWorld.get());
        this.shape = new RecustomCycleButtonWidget<>(0, 0, 150, 20, "Shape", this::shapeClicked, WaypointConfig.Shape.values(), config.shape.get().ordinal(), null);
        this.pointSizeLabel = new TextWidget(100, 20, Text.of("Point Size"), textRenderer);
        this.pointSize = new RecustomTextFieldWidget(textRenderer, 0, 0, 46, 16, null);
        this.pointSize.setTypedChangeListener(this::pointSizeChanged);
        this.maxMapUiScaleLabel = new TextWidget(100, 20, Text.of("Max Map UI Scale"), textRenderer);
        this.maxMapUiScale = new RecustomTextFieldWidget(textRenderer, 0, 0, 46, 16, null);
        this.maxMapUiScale.setTypedChangeListener(this::maxMapUiScaleChanged);
        this.maxWorldUiScaleLabel = new TextWidget(100, 20, Text.of("Max World UI Scale"), textRenderer);
        this.maxWorldUiScale = new RecustomTextFieldWidget(textRenderer, 0, 0, 46, 16, null);
        this.maxWorldUiScale.setTypedChangeListener(this::maxWorldUiScaleChanged);
        this.hideCoords = new RecustomToggleButtonWidget(0, 0, 150, 20, "Hide Coords", this::hideCoordsClicked, config.hideCoords.get());
        this.defaultVisibleDistanceLabel = new TextWidget(100, 20, Text.of("Def. Visible Filter"), textRenderer);
        this.defaultVisibleDistance = new RecustomTextFieldWidget(textRenderer, 0, 0, 46, 16, null);
        this.defaultVisibleDistance.setTypedChangeListener(this::defaultVisibleDistanceChanged);
        this.defaultExpandDistanceLabel = new TextWidget(100, 20, Text.of("Def. Expand Filter"), textRenderer);
        this.defaultExpandDistance = new RecustomTextFieldWidget(textRenderer, 0, 0, 46, 16, null);
        this.defaultExpandDistance.setTypedChangeListener(this::defaultExpandDistanceChanged);
        this.deathWaypoints = new RecustomToggleButtonWidget(0, 0, 150, 20, "Death Waypoints", this::deathWaypointsClicked, config.deathWaypoints.get());
        this.defaultDeathGroupNameLabel = new TextWidget(100, 20, Text.of("Default Death Group"), textRenderer);
        this.defaultDeathGroupName = new RecustomTextFieldWidget(textRenderer, 0, 0, 46, 16, null);
        this.defaultDeathGroupName.setTypedChangeListener(this::defaultDeathGroupNameChanged);
        this.defaultDeathPointNameLabel = new TextWidget(100, 20, Text.of("Default Death Point"), textRenderer);
        this.defaultDeathPointName = new RecustomTextFieldWidget(textRenderer, 0, 0, 46, 16, null);
        this.defaultDeathPointName.setTypedChangeListener(this::defaultDeathPointNameChanged);
        this.showDistanceInWorld = new RecustomToggleButtonWidget(0, 0, 150, 20, "Distance (In-World)", this::showDistanceInWorldClicked, config.showDistanceInWorld.get());
        this.showDistanceOnMap = new RecustomToggleButtonWidget(0, 0, 150, 20, "Distance (On-Map)", this::showDistanceOnMapClicked, config.showDistanceOnMap.get());
        this.ignoreVisibleFilterOnMapScreen = new RecustomToggleButtonWidget(0, 0, 150, 20, "Ignore VFilter/MScrn", this::ignoreVisibleFilterOnMapScreenClicked, config.ignoreVisibleFilterOnMapScreen.get());
        this.ignoreExpandFilterOnMapScreen = new RecustomToggleButtonWidget(0, 0, 150, 20, "Ignore EFilter/MScrn", this::ignoreExpandFilterOnMapScreenClicked, config.ignoreExpandFilterOnMapScreen.get());
        this.done = new RecustomIconButtonWidget(0, 0, 150, 20, "Done", this::doneClicked);

        this.pointSize.setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
        this.pointSize.setText(Integer.toString(config.pointSize.get()));
        this.maxMapUiScale.setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
        this.maxMapUiScale.setText(Integer.toString(config.maxMapUiScale.get()));
        this.maxWorldUiScale.setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
        this.maxWorldUiScale.setText(Integer.toString(config.maxWorldUiScale.get()));
        this.defaultVisibleDistance.setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
        this.defaultVisibleDistance.setText(Integer.toString(config.defaultVisibleDistance.get()));
        this.defaultExpandDistance.setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
        this.defaultExpandDistance.setText(Integer.toString(config.defaultExpandDistance.get()));
        this.defaultDeathGroupName.setText(config.defaultDeathGroupName.get());
        this.defaultDeathPointName.setText(config.defaultDeathPointName.get());
    }

    @Override
    protected void init() {
        addDrawable(titleText);
        addDrawableChild(showOnMinimap);
        addDrawableChild(showOnMapScreen);
        addDrawableChild(showInWorld);
        addDrawableChild(hideCoords);
        this.hideCoords.active = false;
        addDrawable(maxMapUiScaleLabel);
        addDrawableChild(maxMapUiScale);
        addDrawable(maxWorldUiScaleLabel);
        addDrawableChild(maxWorldUiScale);
        addDrawable(pointSizeLabel);
        addDrawableChild(pointSize);
        addDrawableChild(deathWaypoints);
        addDrawable(defaultDeathGroupNameLabel);
        addDrawableChild(defaultDeathGroupName);
        addDrawable(defaultDeathPointNameLabel);
        addDrawableChild(defaultDeathPointName);
        addDrawable(defaultVisibleDistanceLabel);
        addDrawableChild(defaultVisibleDistance);
        addDrawable(defaultExpandDistanceLabel);
        addDrawableChild(defaultExpandDistance);
        addDrawableChild(showDistanceInWorld);
        addDrawableChild(showDistanceOnMap);
        addDrawableChild(ignoreVisibleFilterOnMapScreen);
        addDrawableChild(ignoreExpandFilterOnMapScreen);
        addDrawableChild(shape);
        this.shape.active = false;
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
        titleText.setPosition(x, y);
        y += 10;

        showOnMinimap.setPosition(midX - 152, y);
        showOnMapScreen.setPosition(midX + 2, y);
        y += 22;

        showInWorld.setPosition(midX - 152, y);
        hideCoords.setPosition(midX + 2, y);
        y += 22;

        maxMapUiScaleLabel.setPosition(midX - 152, y);
        maxMapUiScale.setPosition(midX - 50, y + 2);
        maxWorldUiScaleLabel.setPosition(midX + 2, y);
        maxWorldUiScale.setPosition(midX + 104, y + 2);
        y += 22;

        pointSizeLabel.setPosition(midX - 152, y);
        pointSize.setPosition(midX - 50, y + 2);
        deathWaypoints.setPosition(midX + 2, y);
        y += 22;

        defaultDeathGroupNameLabel.setPosition(midX - 152, y);
        defaultDeathGroupName.setPosition(midX - 50, y + 2);
        defaultDeathPointNameLabel.setPosition(midX + 2, y);
        defaultDeathPointName.setPosition(midX + 104, y);
        y += 22;

        showDistanceInWorld.setPosition(midX - 152, y);
        showDistanceOnMap.setPosition(midX + 2, y);
        y += 22;

        defaultVisibleDistanceLabel.setPosition(midX - 152, y);
        defaultVisibleDistance.setPosition(midX - 50, y + 2);
        defaultExpandDistanceLabel.setPosition(midX + 2, y);
        defaultExpandDistance.setPosition(midX + 104, y + 2);
        y += 22;

        ignoreVisibleFilterOnMapScreen.setPosition(midX - 152, y);
        ignoreExpandFilterOnMapScreen.setPosition(midX + 2, y);
        y += 22;

        shape.setPosition(midX - 152, y);
        y += 22;

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

    private void showOnMinimapClicked(ButtonWidget btn) {
        config.showOnMinimap.set(showOnMinimap.isToggled());
    }

    private void showOnMapScreenClicked(ButtonWidget btn) {
        config.showOnMapScreen.set(showOnMapScreen.isToggled());
    }

    private void showInWorldClicked(ButtonWidget btn) {
        config.showInWorld.set(showInWorld.isToggled());
    }

    private void shapeClicked(ButtonWidget btn) {
        config.shape.set(shape.getCurrentValue());
    }

    private void pointSizeChanged(String text) {
        try {
            config.pointSize.set(Integer.parseInt(text));
        } catch (NumberFormatException ignore) {}
    }

    private void maxMapUiScaleChanged(String text) {
        try {
            config.maxMapUiScale.set(Integer.parseInt(text));
        } catch (NumberFormatException ignore) {}
    }

    private void maxWorldUiScaleChanged(String text) {
        try {
            config.maxWorldUiScale.set(Integer.parseInt(text));
        } catch (NumberFormatException ignore) {}
    }

    private void hideCoordsClicked(ButtonWidget btn) {
        config.hideCoords.set(hideCoords.isToggled());
    }

    private void defaultVisibleDistanceChanged(String text) {
        try {
            config.defaultVisibleDistance.set(Integer.parseInt(text));
        } catch (NumberFormatException ignore) {}
    }

    private void defaultExpandDistanceChanged(String text) {
        try {
            config.defaultVisibleDistance.set(Integer.parseInt(text));
        } catch (NumberFormatException ignore) {}
    }

    private void deathWaypointsClicked(ButtonWidget btn) {
        config.deathWaypoints.set(deathWaypoints.isToggled());
    }

    private void defaultDeathGroupNameChanged(String text) {
        if (!text.isEmpty()) {
            config.defaultDeathGroupName.set(text);
        }
    }

    private void defaultDeathPointNameChanged(String text) {
        if (!text.isEmpty()) {
            config.defaultDeathPointName.set(text);
        }
    }

    private void showDistanceInWorldClicked(ButtonWidget btn) {
        config.showDistanceInWorld.set(showDistanceInWorld.isToggled());
    }

    private void showDistanceOnMapClicked(ButtonWidget btn) {
        config.showDistanceOnMap.set(showDistanceOnMap.isToggled());
    }

    private void ignoreVisibleFilterOnMapScreenClicked(ButtonWidget btn) {
        config.ignoreVisibleFilterOnMapScreen.set(ignoreVisibleFilterOnMapScreen.isToggled());
    }

    private void ignoreExpandFilterOnMapScreenClicked(ButtonWidget btn) {
        config.ignoreExpandFilterOnMapScreen.set(ignoreExpandFilterOnMapScreen.isToggled());
    }

    private void doneClicked(ButtonWidget btn) {
        client.setScreen(parentScreen);
    }
}
