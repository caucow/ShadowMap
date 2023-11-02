package com.caucraft.shadowmap.client.gui.waypoint;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.gui.IconAtlas;
import com.caucraft.shadowmap.client.gui.Icons;
import com.caucraft.shadowmap.client.gui.LessPoopScreen;
import com.caucraft.shadowmap.client.gui.component.RecustomIconButtonWidget;
import com.caucraft.shadowmap.client.map.MapWorldImpl;
import com.caucraft.shadowmap.client.waypoint.WorldWaypointManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.util.UUID;

public class WaypointListScreen extends LessPoopScreen {

    private final ShadowMap shadowMap;
    private final Screen previousScreen;
    private final WorldWaypointManager waypointManager;
    private WaypointListWidget waypointList;
    private RecustomIconButtonWidget addWaypointButton;
    private RecustomIconButtonWidget addWaypointToButton;
    private RecustomIconButtonWidget addGroupButton;
    private RecustomIconButtonWidget addGroupToButton;
    private RecustomIconButtonWidget clearHighlightsButton;
    private ButtonWidget backButton;

    public WaypointListScreen(ShadowMap shadowMap, Screen previousScreen) {
        super(Text.of("Waypoints"));
        this.shadowMap = shadowMap;
        this.previousScreen = previousScreen;
        MapWorldImpl world = shadowMap.getMapManager().getCurrentWorld();
        if (world == null) {
            waypointManager = null;
        } else {
            waypointManager = world.getWaypointManager();
        }
    }

    @Override
    protected void init() {
        this.waypointList = addDrawableChild(new WaypointListWidget(client, waypointManager, this, width, height, true));
        this.addWaypointButton = addDrawableChild(new RecustomIconButtonWidget(0, 0, 125, 20, "Add Waypoint", (btn) -> addWaypoint(false, false)));
        this.addWaypointToButton = addDrawableChild(new RecustomIconButtonWidget(0, 0, 125, 20, "Add Waypoint To...", (btn) -> addWaypoint(false, true)));
        this.addGroupButton = addDrawableChild(new RecustomIconButtonWidget(0, 0, 125, 20, "Add Group", (btn) -> addWaypoint(true, false)));
        this.addGroupToButton = addDrawableChild(new RecustomIconButtonWidget(0, 0, 125, 20, "Add Group To...", (btn) -> addWaypoint(true, true)));
        this.clearHighlightsButton = addDrawableChild(new RecustomIconButtonWidget(0, 0, 100, 20, "Clear Highlights", (btn) -> waypointManager.clearHighlights()));
        this.backButton = addDrawableChild(ButtonWidget.builder(Text.of("Back"), (btn) -> client.setScreen(previousScreen)).dimensions(0, 0, 100, 20).build());

        waypointList.setSelectHandler((wpoint) -> updateButtonStates());
        waypointList.setRenderBackground(false);

        IconAtlas atlas = shadowMap.getIconAtlas();
        addWaypointButton.setIcon(atlas.getIcon(Icons.ADD_WAYPOINT));
        addGroupButton.setIcon(atlas.getIcon(Icons.ADD_GROUP));
        addWaypointToButton.setIcon(atlas.getIcon(Icons.ADD_WAYPOINT));
        addGroupToButton.setIcon(atlas.getIcon(Icons.ADD_GROUP));

        resize(client, width, height);
        updateButtonStates();
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        this.width = width;
        this.height = height;

        waypointList.setSize(width, height);

        int midX = width / 2;
        int x = midX - (addWaypointButton.getWidth() + addWaypointToButton.getWidth() + backButton.getWidth() + 24) / 2;
        int y = height - 60;
        addWaypointButton.setPosition(x, y);
        addGroupButton.setPosition(x, y + 24);
        x += addWaypointButton.getWidth() + 8;
        addWaypointToButton.setPosition(x, y);
        addGroupToButton.setPosition(x, y + 24);
        x += addGroupButton.getWidth() + 16;
        clearHighlightsButton.setPosition(x, y);
        backButton.setPosition(x, y + 24);
    }

    @Override
    public void removed() {

    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float tickDelta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, tickDelta);
    }

    private void addWaypoint(boolean isGroup, boolean addToSelected) {
        Vector3d pos = new Vector3d();
        Entity camera = client.getCameraEntity();
        if (camera == null) {
            camera = client.player;
        }
        if (camera != null) {
            Vec3d ppos = camera.getPos();
            pos.set(ppos.x, ppos.y, ppos.z);
        }
        UUID groupId = null;
        WaypointListWidget.WaypointEntry selectedEntry = waypointList.getSelectedOrNull();
        if (addToSelected && selectedEntry != null) {
            groupId = selectedEntry.waypoint.getId();
        }
        EditWaypointScreen editScreen = new EditWaypointScreen(this, waypointManager, pos, groupId, isGroup);
        client.setScreen(editScreen);
    }

    private void updateButtonStates() {
        addWaypointToButton.active = false;
        addGroupToButton.active = false;
        WaypointListWidget.WaypointEntry selected = waypointList.getSelectedOrNull();
        if (selected == null) {
            return;
        }
        addWaypointToButton.active = true;
        addGroupToButton.active = true;
    }
}
