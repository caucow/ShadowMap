package com.caucraft.shadowmap.client.gui.config;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.gui.component.RecustomIconButtonWidget;
import com.caucraft.shadowmap.client.gui.importer.ImportsScreen;
import com.caucraft.shadowmap.client.gui.waypoint.WaypointListScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

public class CoreConfigScreen extends Screen {
    private final Screen parentScreen;
    private final TextWidget titleText;
    private final RecustomIconButtonWidget minimapConfig;
    private final RecustomIconButtonWidget mapScreenConfig;
    private final RecustomIconButtonWidget infoConfig;
    private final RecustomIconButtonWidget gridConfig;
    private final RecustomIconButtonWidget waypointConfig;
    private final RecustomIconButtonWidget privacyConfig;
    private final RecustomIconButtonWidget performanceConfig;
    private final RecustomIconButtonWidget debugConfig;

    private final RecustomIconButtonWidget waypoints;
    private final RecustomIconButtonWidget imports;

    private final RecustomIconButtonWidget reload;
    private final RecustomIconButtonWidget done;

    public CoreConfigScreen(Screen parentScreen) {
        super(Text.of("ShadowMap Settings"));
        this.parentScreen = parentScreen;
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        this.titleText = new TextWidget(getTitle(), textRenderer);
        this.minimapConfig = new RecustomIconButtonWidget(0, 0, 150, 20, "Minimap Settings", this::minimapConfigClicked);
        this.mapScreenConfig = new RecustomIconButtonWidget(0, 0, 150, 20, "Map Screen Settings", this::mapScreenConfigClicked);
        this.infoConfig = new RecustomIconButtonWidget(0, 0, 150, 20, "HUD Info Settings", this::infoConfigClicked);
        this.gridConfig = new RecustomIconButtonWidget(0, 0, 150, 20, "Chunk Grid Settings", this::gridConfigClicked);
        this.waypointConfig = new RecustomIconButtonWidget(0, 0, 150, 20, "Waypoint Settings", this::waypointConfigClicked);
        this.privacyConfig = new RecustomIconButtonWidget(0, 0, 150, 20, "Privacy Settings", this::privacyConfigClicked);
        this.performanceConfig = new RecustomIconButtonWidget(0, 0, 150, 20, "Performance Settings", this::performanceConfigClicked);
        this.debugConfig = new RecustomIconButtonWidget(0, 0, 150, 20, "Debug Settings", this::debugConfigClicked);
        this.waypoints = new RecustomIconButtonWidget(0, 0, 150, 20, "Waypoints", this::waypointsClicked);
        this.imports = new RecustomIconButtonWidget(0, 0, 150, 20, "Imports", this::importsClicked);
        this.reload = new RecustomIconButtonWidget(0, 0, 150, 20, "Reload Configuration", this::reloadClicked);
        this.done = new RecustomIconButtonWidget(0, 0, 150, 20, "Done", this::doneClicked);
    }

    @Override
    protected void init() {
        addDrawable(titleText);
        addDrawableChild(minimapConfig);
        addDrawableChild(mapScreenConfig);
        addDrawableChild(infoConfig);
        addDrawableChild(gridConfig);
        addDrawableChild(waypointConfig);
        addDrawableChild(privacyConfig);
        addDrawableChild(performanceConfig);
        performanceConfig.active = false; // TODO enable and better implement
//        addDrawableChild(debugConfig);
        addDrawableChild(waypoints);
        if (ShadowMap.getInstance().getMapManager().getCurrentWorld() == null) {
            waypoints.active = false;
        }
        addDrawableChild(imports);
        addDrawableChild(reload);
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
        titleText.setPos(x, y);
        y += 16;

        minimapConfig.setPos(midX - 152, y);
        mapScreenConfig.setPos(midX + 2, y);
        y += 22;

        waypointConfig.setPos(midX - 152, y);
        infoConfig.setPos(midX + 2, y);
        y += 22;

        privacyConfig.setPos(midX - 152, y);
        gridConfig.setPos(midX + 2, y);
        y += 22;

        performanceConfig.setPos(midX - 152, y);
        debugConfig.setPos(midX + 2, y);
        y += 44;

        waypoints.setPos(midX - 152, y);
        imports.setPos(midX + 2, y);

        y = (height - 240) / 3 + 210;
        reload.setPos(midX - 75, y - 22);
        done.setPos(midX - 75, y);
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

    private void minimapConfigClicked(ButtonWidget btn) {
        client.setScreen(new MinimapConfigScreen(this));
    }

    private void mapScreenConfigClicked(ButtonWidget btn) {
        client.setScreen(new MapScreenConfigScreen(this));
    }

    private void infoConfigClicked(ButtonWidget btn) {
        client.setScreen(new InfoConfigScreen(this));
    }

    private void gridConfigClicked(ButtonWidget btn) {
        client.setScreen(new GridConfigScreen(this));
    }

    private void waypointConfigClicked(ButtonWidget btn) {
        client.setScreen(new WaypointConfigScreen(this));
    }

    private void privacyConfigClicked(ButtonWidget btn) {
        client.setScreen(new PrivacyConfigScreen(this));
    }

    private void performanceConfigClicked(ButtonWidget btn) {
        client.setScreen(new PerformanceConfigScreen(this));
    }

    private void debugConfigClicked(ButtonWidget btn) {
        client.setScreen(new DebugConfigScreen(this));
    }

    private void waypointsClicked(ButtonWidget btn) {
        client.setScreen(new WaypointListScreen(ShadowMap.getInstance(), this));
    }

    private void importsClicked(ButtonWidget btn) {
        client.setScreen(new ImportsScreen(this));
    }

    private void reloadClicked(ButtonWidget btn) {
        ShadowMap.getInstance().scheduleLoadConfig();
    }

    private void doneClicked(ButtonWidget btn) {
        client.setScreen(parentScreen);
    }
}
