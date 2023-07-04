package com.caucraft.shadowmap.client.gui.importer;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.gui.component.RecustomCycleButtonWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomIconButtonWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomTextFieldWidget;
import com.caucraft.shadowmap.client.importer.ImportManager;
import com.caucraft.shadowmap.client.importer.ImportSupplier;
import com.caucraft.shadowmap.client.importer.ImportType;
import com.caucraft.shadowmap.client.util.TextHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.util.List;

public class AddImportsScreen extends Screen {
    private final Screen parentScreen;
    private final ImportSupplierListWidget importerList;
    private final RecustomTextFieldWidget manualAddField;
    private final RecustomCycleButtonWidget<ImportType> importTypeButton;
    private final RecustomIconButtonWidget manualAddButton;
    private final RecustomIconButtonWidget autoScanButton;
    private final RecustomIconButtonWidget enableAllButton;
    private final RecustomIconButtonWidget disableAllButton;
    private final RecustomIconButtonWidget importButton;
    private final RecustomIconButtonWidget cancelButton;

    public AddImportsScreen(Screen parentScreen) {
        super(Text.of("Import Maps and Waypoints"));
        this.parentScreen = parentScreen;
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        this.importerList = new ImportSupplierListWidget(client, width, height, 25, height - 80);
        this.importerList.setRenderBackground(false);
        this.manualAddField = new RecustomTextFieldWidget(textRenderer, 0, 0, 215, 16, null);
        this.manualAddField.setMaxLength(65536);
        this.manualAddField.setSuggestionHint("World Folder or Waypoint File");
        this.importTypeButton = new RecustomCycleButtonWidget<>(0, 0, 100, 20, "Type", this::onTypeClicked, ImportType.values(), 0, null);
        this.manualAddButton = new RecustomIconButtonWidget(0, 0, 60, 20, "Add", this::onAddClicked);
        this.autoScanButton = new RecustomIconButtonWidget(0, 0, 80, 20, "Re-Scan", this::onScanClicked);
        this.enableAllButton = new RecustomIconButtonWidget(0, 0, 80, 20, "Enable All", this::onEnableAllClicked);
        this.disableAllButton = new RecustomIconButtonWidget(0, 0, 80, 20, "Disable All", this::onDisableAllClicked);
        this.importButton = new RecustomIconButtonWidget(0, 0, 150, 20, "Start Imports", this::onImportClicked);
        this.cancelButton = new RecustomIconButtonWidget(0, 0, 150, 20, "Cancel", this::onCancelClicked);

        importerList.refreshImporters();
    }

    @Override
    protected void init() {
        addDrawableChild(importerList);
        addDrawableChild(manualAddField);
        addDrawableChild(importTypeButton);
        addDrawableChild(manualAddButton);
        addDrawableChild(autoScanButton);
        addDrawableChild(enableAllButton);
        addDrawableChild(disableAllButton);
        addDrawableChild(importButton);
        addDrawableChild(cancelButton);

        resize(client, width, height);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        this.width = width;
        this.height = height;

        importerList.setSize(width, height);

        int midX = width / 2;
        int x = midX - (manualAddField.getWidth() + importTypeButton.getWidth() + 5) / 2;
        int y = height - 75;
        manualAddField.setPosition(x, y + 2);
        x += manualAddField.getWidth() + 5;
        importTypeButton.setPosition(x, y);

        x = midX - (enableAllButton.getWidth() + disableAllButton.getWidth() + manualAddButton.getWidth() + autoScanButton.getWidth() + 20) / 2;
        y = height - 50;
        enableAllButton.setPosition(x, y);
        x += enableAllButton.getWidth() + 4;
        disableAllButton.setPosition(x, y);
        x += disableAllButton.getWidth() + 4;
        autoScanButton.setPosition(x, y);
        x += autoScanButton.getWidth() + 12;
        manualAddButton.setPosition(x, y);

        x = midX - (importButton.getWidth() + cancelButton.getWidth() + 20) / 2;
        y = height - 25;
        importButton.setPosition(x, y);
        x += importButton.getWidth() + 20;
        cancelButton.setPosition(x, y);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
        TextHelper.get(textRenderer, matrices).drawCentered(title, width / 2, 10);
    }

    private void onAddClicked(ButtonWidget widget) {

    }

    private void onScanClicked(ButtonWidget widget) {
        importerList.refreshImporters();
    }

    private void onTypeClicked(ButtonWidget widget) {

    }

    private void onEnableAllClicked(ButtonWidget widget) {
        importerList.setAllEnabled(true);
    }

    private void onDisableAllClicked(ButtonWidget widget) {
        importerList.setAllEnabled(false);
    }

    private void onImportClicked(ButtonWidget widget) {
        ImportManager importManager = ShadowMap.getInstance().getMapManager().getImportManager();
        List<ImportSupplier> importers = importerList.getImporters();
        for (ImportSupplier importer : importers) {
            importManager.addTask(importer.getImportTask());
        }
        client.setScreen(parentScreen);
    }

    private void onCancelClicked(ButtonWidget widget) {
        client.setScreen(parentScreen);
    }
}
