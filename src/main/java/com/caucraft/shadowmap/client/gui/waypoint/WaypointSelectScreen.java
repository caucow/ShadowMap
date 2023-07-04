package com.caucraft.shadowmap.client.gui.waypoint;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.gui.LessPoopScreen;
import com.caucraft.shadowmap.client.map.MapWorldImpl;
import com.caucraft.shadowmap.client.waypoint.Waypoint;
import com.caucraft.shadowmap.client.waypoint.WorldWaypointManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class WaypointSelectScreen extends LessPoopScreen {
    private final ShadowMap shadowMap;
    private final Screen previousScreen;
    private final WorldWaypointManager waypointManager;
    private final Consumer<Waypoint> selectCallback;
    private WaypointListWidget waypointList;
    private ButtonWidget selectButton;
    private ButtonWidget selectNoneButton;
    private ButtonWidget backButton;

    public WaypointSelectScreen(ShadowMap shadowMap, Screen previousScreen, Text title, Consumer<Waypoint> selectCallback) {
        super(title);
        this.shadowMap = shadowMap;
        this.previousScreen = previousScreen;
        this.selectCallback = selectCallback;
        MapWorldImpl world = shadowMap.getMapManager().getCurrentWorld();
        if (world == null) {
            this.waypointManager = null;
        } else {
            this.waypointManager = world.getWaypointManager();
        }
    }

    @Override
    protected void init() {
        this.waypointList = addDrawableChild(new WaypointListWidget(client, waypointManager, this, width, height, false));
        this.selectButton = addDrawableChild(ButtonWidget.builder(Text.of("Select"), (btn) -> {
            selectCallback.accept(waypointList.getSelectedOrNull().waypoint);
            client.setScreen(previousScreen);
        }).dimensions(0, 0, 100, 20).build());
        this.selectNoneButton = addDrawableChild(ButtonWidget.builder(Text.of("Select None"), (btn) -> {
            selectCallback.accept(null);
            client.setScreen(previousScreen);
        }).dimensions(0, 0, 100, 20).build());
        this.backButton = addDrawableChild(ButtonWidget.builder(Text.of("Back"), (btn) -> client.setScreen(previousScreen)).dimensions(0, 0, 100, 20).build());

        waypointList.setSelectHandler((wpoint) -> selectButton.active = wpoint != null);
        selectButton.active = false;

        resize(client, width, height);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        this.width = width;
        this.height = height;

        int midX = width / 2;
        int x = midX - (selectButton.getWidth() + selectNoneButton.getWidth() + backButton.getWidth() + 16) / 2;
        int y = height - 60;

        selectButton.setPosition(x, y);
        x += selectButton.getWidth() + 8;
        selectNoneButton.setPosition(x, y);
        x += selectNoneButton.getWidth() + 8;
        backButton.setPosition(x, y);
    }

    @Override
    public void removed() {}
}
