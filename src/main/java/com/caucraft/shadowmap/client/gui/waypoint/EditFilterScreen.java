package com.caucraft.shadowmap.client.gui.waypoint;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.gui.IconAtlas;
import com.caucraft.shadowmap.client.gui.Icons;
import com.caucraft.shadowmap.client.gui.LessPoopScreen;
import com.caucraft.shadowmap.client.gui.component.RecustomCycleButtonWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomIconButtonWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomTextFieldWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomToggleButtonWidget;
import com.caucraft.shadowmap.client.waypoint.WaypointFilter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class EditFilterScreen extends LessPoopScreen {

    private final EditWaypointScreen parentScreen;
    private final WaypointFilter filter;
    private final Consumer<WaypointFilter> filterCallback;
    private TextWidget titleText;
    private RecustomToggleButtonWidget enabledToggle;
    private TextWidget radiusText;
    private RecustomTextFieldWidget radiusField;
    private RecustomCycleButtonWidget<WaypointFilter.Shape> shapeButton;
    private RecustomIconButtonWidget saveButton;
    private RecustomIconButtonWidget cancelButton;

    public EditFilterScreen(EditWaypointScreen parentScreen, Text title, WaypointFilter filter, int defaultRadius, Consumer<WaypointFilter> filterCallback) {
        super(title);
        this.parentScreen = parentScreen;
        if (filter == null) {
            this.filter = filter = new WaypointFilter();
            filter.setRadius(defaultRadius);
        } else {
            this.filter = filter = new WaypointFilter(filter);
        }
        this.filterCallback = filterCallback;
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        IconAtlas atlas = ShadowMap.getInstance().getIconAtlas();
        this.titleText = new TextWidget(title, textRenderer);
        this.enabledToggle = new RecustomToggleButtonWidget(0, 0, 100, 20, "Enabled", (btn) -> {}, filter.isEnabled());
        enabledToggle.setIcons(atlas.getIcon(Icons.FILTER_OFF), atlas.getIcon(Icons.FILTER_ON));
        this.radiusText = new TextWidget(Text.of("Radius"), textRenderer);
        this.radiusField = new RecustomTextFieldWidget(textRenderer, 0, 0, 70, 16, Text.empty());
        radiusField.setText(Integer.toString(filter.getRadius()));
        this.shapeButton = new RecustomCycleButtonWidget<>(0, 0, 100, 20, "Shape", (btn) -> {},
                WaypointFilter.Shape.values(), filter.getShape().ordinal(),
                new Sprite[] {atlas.getIcon(Icons.CIRCLE), atlas.getIcon(Icons.SQUARE), atlas.getIcon(Icons.OCTAGON)});
        // TODO implement shape rendering, map clicks, etc. and re-enable this.
        shapeButton.active = false;
        this.saveButton = new RecustomIconButtonWidget(0, 0, 100, 20, "Save", this::saveClicked);
        this.cancelButton = new RecustomIconButtonWidget(0, 0, 100, 20, "Cancel", this::cancelClicked);
    }

    @Override
    protected void init() {
        addDrawable(titleText);
        addDrawableChild(enabledToggle);
        addDrawable(radiusText);
        addDrawableChild(radiusField);
        addDrawableChild(shapeButton);
        addDrawableChild(saveButton);
        addDrawableChild(cancelButton);

        resize(client, width, height);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        this.width = width;
        this.height = height;
        final int midX = width / 2;
        final int ty = (height - 240) / 3;

        int x = midX - (titleText.getWidth()) / 2;
        int y = ty + 10;
        titleText.setPosition(x, y);

        x = midX - (enabledToggle.getWidth()) / 2;
        y += 18;
        enabledToggle.setPosition(x, y);

        x = midX - (radiusText.getWidth() + radiusField.getWidth() + 4) / 2;
        y += 24;
        radiusText.setPosition(x, y + 5);
        x += radiusText.getWidth() + 4;
        radiusField.setPosition(x, y);

        x = midX - (shapeButton.getWidth()) / 2;
        y += 20;
        shapeButton.setPosition(x, y);

        x = midX - (saveButton.getWidth() + cancelButton.getWidth() + 10) / 2;
        y = ty + 210;
        saveButton.setPosition(x, y);
        x += saveButton.getWidth() + 10;
        cancelButton.setPosition(x, y);
    }

    private void saveClicked(ButtonWidget btn) {
        filter.setEnabled(enabledToggle.isToggled());
        try { filter.setRadius(Integer.parseInt(radiusField.getText())); } catch (NumberFormatException ignore) {};
        filter.setShape(shapeButton.getCurrentValue());
        if (filterCallback != null) {
            filterCallback.accept(filter);
        }
        client.setScreen(parentScreen);
    }

    private void cancelClicked(ButtonWidget btn) {
        client.setScreen(parentScreen);
    }
}
