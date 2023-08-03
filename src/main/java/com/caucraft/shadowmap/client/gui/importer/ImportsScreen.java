package com.caucraft.shadowmap.client.gui.importer;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.gui.LessPoopScreen;
import com.caucraft.shadowmap.client.gui.component.ConfirmDialogWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomIconButtonWidget;
import com.caucraft.shadowmap.client.util.TextHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

public class ImportsScreen extends LessPoopScreen {
    private final Screen parentScreen;
    private final ImportListWidget importList;
    private final RecustomIconButtonWidget pauseAllButton;
    private final RecustomIconButtonWidget unpauseAllButton;
    private final RecustomIconButtonWidget removeAllButton;
    private final RecustomIconButtonWidget removeButton;
    private final RecustomIconButtonWidget importButton;
    private final RecustomIconButtonWidget backButton;

    public ImportsScreen(Screen parentScreen) {
        super(Text.of("Imports"));
        this.parentScreen = parentScreen;
        this.importList = new ImportListWidget(MinecraftClient.getInstance(), ShadowMap.getInstance().getMapManager().getImportManager(), width, height, 30, height - 55);
        this.importList.setRenderBackground(false);
        this.pauseAllButton = new RecustomIconButtonWidget(0, 0, 73, 20, "Pause All", this::onPauseAllClicked);
        this.unpauseAllButton = new RecustomIconButtonWidget(0, 0, 73, 20, "Unpause All", this::onUnpauseAllClicked);
        this.removeAllButton = new RecustomIconButtonWidget(0, 0, 73, 20, "Remove All", this::onRemoveAllClicked);
        this.removeButton = new RecustomIconButtonWidget(0, 0, 73, 20, "Remove", this::onRemoveClicked);
        this.importButton = new RecustomIconButtonWidget(0, 0, 150, 20, "Add Importers...", this::onImportClicked);
        this.backButton = new RecustomIconButtonWidget(0, 0, 150, 20, "Back", this::onDoneClicked);
    }

    @Override
    protected void init() {
        importList.refreshTasks();

        addDrawableChild(importList);
        addDrawableChild(pauseAllButton);
        addDrawableChild(unpauseAllButton);
        addDrawableChild(removeAllButton);
        addDrawableChild(removeButton);
        addDrawableChild(importButton);
        addDrawableChild(backButton);

        resize(client, width, height);
    }

    private void onPauseAllClicked(ButtonWidget btn) {
        importList.setAllPaused(true);
    }

    private void onUnpauseAllClicked(ButtonWidget btn) {
        importList.setAllPaused(false);
    }

    private void onRemoveAllClicked(ButtonWidget btn) {
        confirmAction("Remove all Imports?",
                new ConfirmDialogWidget.Option("Remove All Done", null, false, () -> {
                    importList.removeAll(false);
                }),
                new ConfirmDialogWidget.Option("Remove All", null, false, () -> {
                    importList.removeAll(true);
                }),
                new ConfirmDialogWidget.Option("Cancel", null, true, () -> {})
        );
    }

    private void onRemoveClicked(ButtonWidget btn) {
        importList.removeSelected();
    }

    private void onImportClicked(ButtonWidget btn) {
        client.setScreen(new AddImportsScreen(this));
    }

    private void onDoneClicked(ButtonWidget btn) { // TODO rename onBackClicked
        client.setScreen(parentScreen);
    }

    @Override
    public void tick() {
        importList.tick();
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        this.width = width;
        this.height = height;
        importList.setSize(width, height);

        int midX = width / 2;
        int x = midX - (pauseAllButton.getWidth() + unpauseAllButton.getWidth() + removeAllButton.getWidth() + removeButton.getWidth() + 28) / 2;
        int y = height - 50;
        pauseAllButton.setPosition(x, y);
        x += pauseAllButton.getWidth() + 4;
        unpauseAllButton.setPosition(x, y);
        x += unpauseAllButton.getWidth() + 20;
        removeAllButton.setPosition(x, y);
        x += removeAllButton.getWidth() + 4;
        removeButton.setPosition(x, y);

        x = midX - (importButton.getWidth() + backButton.getWidth() + 20) / 2;
        y = height - 25;
        importButton.setPosition(x, y);
        x += importButton.getWidth() + 20;
        backButton.setPosition(x, y);
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        renderBackground(drawContext);
        super.render(drawContext, mouseX, mouseY, delta);
        TextHelper.get(textRenderer, drawContext.getMatrices()).drawCentered(title, width / 2, 10);
    }
}
