package com.caucraft.shadowmap.client.gui.importer;

import com.caucraft.shadowmap.api.util.ServerKey;
import com.caucraft.shadowmap.client.gui.component.RecustomCycleButtonWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomTextFieldWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomToggleButtonWidget;
import com.caucraft.shadowmap.client.importer.ImportScanner;
import com.caucraft.shadowmap.client.importer.ImportSupplier;
import com.caucraft.shadowmap.client.importer.ImportType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

public class ImportSupplierListWidget extends AlwaysSelectedEntryListWidget<ImportSupplierListWidget.StupidAbstraction> {
    private final Map<String, ServerLabelEntry> servers;
    private int importRefreshIndex;

    public ImportSupplierListWidget(MinecraftClient minecraftClient, int width, int height, int top, int bottom) {
        super(minecraftClient, width, height, top, bottom, 40);
        servers = new TreeMap<>();
        importRefreshIndex = -1;
    }

    public void setSize(int width, int height) {
        int bottomDiff = this.bottom - this.height;
        updateSize(width, height, top, bottomDiff + height);
    }

    void refreshImporters() {
        if (importRefreshIndex != -1) {
            return;
        }
        removeAutoGenerated();
        importRefreshIndex = children().size();
        addEntry(new LoadingEntry());
        CompletableFuture<List<ImportSupplier>> scanFuture = CompletableFuture.completedFuture(new ArrayList<>());
        for (ImportType importType : ImportType.values()) {
            ImportScanner scanner = importType.getScanner();
            scanFuture = scanFuture.thenCombine(scanner.getImportSuppliers(), (fullList, partialList) -> {
                fullList.addAll(partialList);
                return fullList;
            });
        }
        scanFuture.thenAccept((list) -> MinecraftClient.getInstance().send(() -> {
            for (ImportSupplier importer : list) {
                addImporter(importer);
            }
        })).whenComplete((val, ex) -> {
            remove(importRefreshIndex);
            importRefreshIndex = -1;
        });
    }

    public void addImporter(ImportSupplier importer) {
        ServerLabelEntry serverLabel = servers.computeIfAbsent(importer.getServerName(), (key) -> {
            ServerLabelEntry newLabel = new ServerLabelEntry(key);
            addEntry(newLabel);
            return newLabel;
        });
        ImportScanEntry entry = new ImportScanEntry(importer, serverLabel);
        serverLabel.worldEntries.add(entry);
        children().add(children().indexOf(serverLabel) + serverLabel.worldEntries.size(), entry);
    }

    public void removeAutoGenerated() {
        servers.clear();
        for (int i = getEntryCount() - 1; i >= 0; i--) {
            StupidAbstraction bs = getEntry(i);
            if (bs instanceof ServerLabelEntry || bs instanceof ImportScanEntry entry && !entry.importer.isManuallyAdded()) {
                remove(i);
            }
        }
    }

    public void setAllEnabled(boolean enabled) {
        for (int i = getEntryCount() - 1; i >= 0; i--) {
            StupidAbstraction bs = getEntry(i);
            if (bs instanceof ImportScanEntry entry) {
                entry.setEnabled(enabled);
            }
        }
    }

    public List<ImportSupplier> getImporters() {
        List<ImportSupplier> importers = new ArrayList<>();
        for (int i = getEntryCount() - 1; i >= 0; i--) {
            StupidAbstraction bs = getEntry(i);
            if (bs instanceof ImportScanEntry entry && entry.enabled.isToggled()) {
                importers.add(entry.importer);
            }
        }
        return importers;
    }

    @Override
    public int getRowWidth() {
        return 320;
    }

    @Override
    protected int getScrollbarPositionX() {
        return width / 2 + 164;
    }

    abstract class StupidAbstraction extends Entry<StupidAbstraction> {
        final List<ClickableWidget> childListBecauseINeedToMakeItMyself;

        StupidAbstraction() {
            this.childListBecauseINeedToMakeItMyself = new ArrayList<>();
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (!focused) {
                for (ClickableWidget widget : childListBecauseINeedToMakeItMyself) {
                    widget.setFocused(false);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            boolean selected = getSelectedOrNull() != this;
            setSelected(this);
            int x = getRowLeft();
            int y = getRowTop(ImportSupplierListWidget.this.children().indexOf(this));
            for (ClickableWidget widget : childListBecauseINeedToMakeItMyself) {
                if (widget.mouseClicked(mouseX - x, mouseY - y, button)) {
                    widget.setFocused(true);
                } else {
                    widget.setFocused(false);
                }
            }
            return selected;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            int x = getRowLeft();
            int y = getRowTop(ImportSupplierListWidget.this.children().indexOf(this));
            for (ClickableWidget widget : childListBecauseINeedToMakeItMyself) {
                widget.mouseReleased(mouseX - x, mouseY - y, button);
            }
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            for (ClickableWidget widget : childListBecauseINeedToMakeItMyself) {
                widget.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }

        @Override
        public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
            for (ClickableWidget widget : childListBecauseINeedToMakeItMyself) {
                widget.keyReleased(keyCode, scanCode, modifiers);
            }
            return true;
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            for (ClickableWidget widget : childListBecauseINeedToMakeItMyself) {
                widget.charTyped(chr, modifiers);
            }
            return true;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX,
                int mouseY, boolean hovered, float tickDelta) {
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(x, y, 0);
            for (ClickableWidget widget : childListBecauseINeedToMakeItMyself) {
                widget.render(context, mouseX - x, mouseY - y, tickDelta);
            }
            matrices.pop();
        }
    }

    class LoadingEntry extends StupidAbstraction {
        final TextWidget label;

        LoadingEntry() {
            this.label = new TextWidget(2, 2, 320, itemHeight - 4, Text.of("Loading..."), MinecraftClient.getInstance().textRenderer);

            childListBecauseINeedToMakeItMyself.add(label);
        }

        @Override
        public Text getNarration() {
            return label.getMessage();
        }
    }

    class ServerLabelEntry extends StupidAbstraction {
        final List<ImportScanEntry> worldEntries;
        final TextWidget label;
        final RecustomTextFieldWidget server;

        ServerLabelEntry(String serverAddress) {
            this.worldEntries = new ArrayList<>();

            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            this.label = new TextWidget(Text.of("Server Address"), textRenderer);
            this.label.setPosition(0, 15);
            this.server = new RecustomTextFieldWidget(textRenderer, 1, 25, 314, 10, null);
            this.server.setMaxLength(500);
            this.server.setText(serverAddress);
            this.server.setTypedChangeListener((text) -> updateImporters());

            childListBecauseINeedToMakeItMyself.add(label);
            childListBecauseINeedToMakeItMyself.add(server);
        }

        private void updateImporters() {
            for (ImportScanEntry entry : worldEntries) {
                entry.importer.setServerName(server.getText().toLowerCase(Locale.ROOT));
            }
        }

        @Override
        public Text getNarration() {
            return Text.of(server.getText());
        }
    }

    class ImportScanEntry extends StupidAbstraction {
        final ImportSupplier importer;
        final ServerLabelEntry server;

        final RecustomToggleButtonWidget enabled;
        // TODO add ImportType readout
        final RecustomCycleButtonWidget<ServerKey.ServerType> serverType;
        final RecustomToggleButtonWidget usesDefaultData;
        final TextWidget portLabel;
        final RecustomTextFieldWidget port;
        final TextWidget worldLabel;
        final RecustomTextFieldWidget world;
        final TextWidget dimensionLabel;
        final RecustomTextFieldWidget dimension;
        final TextWidget importType;
        final TextWidget importPath;

        ImportScanEntry(ImportSupplier importer, ServerLabelEntry server) {
            this.importer = importer;
            this.server = server;
            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            this.enabled = new RecustomToggleButtonWidget(0, 0, 80, 12, "Enabled", this::onEnabledClicked, true);
            this.serverType = new RecustomCycleButtonWidget<>(81, 0, 130, 12, "Map Type", (btn) -> updateImporter(),
                    ServerKey.ServerType.values(), importer.getServerType().ordinal(), null);
            this.usesDefaultData = new RecustomToggleButtonWidget(212, 0, 104, 12, "Default Data", (btn) -> updateImporter(), true);

            this.portLabel = new TextWidget(Text.of("Port:"), textRenderer);
            this.portLabel.setPosition(0, 16);
            this.port = new RecustomTextFieldWidget(textRenderer, 26, 14, 38, 10, null);
            this.port.setText(Integer.toString(importer.getServerPort()));
            this.port.setTextPredicate((text) -> text.matches("\\d{0,5}"));
            this.port.setTypedChangeListener((text) -> updateImporter());
            this.worldLabel = new TextWidget(Text.of("World:"), textRenderer);
            this.worldLabel.setPosition(69, 16);
            this.world = new RecustomTextFieldWidget(textRenderer, 100, 14, 85, 10, null);
            this.world.setMaxLength(100);
            this.world.setText(importer.getWorldName());
            this.world.setTypedChangeListener((text) -> updateImporter());
            this.dimensionLabel = new TextWidget(Text.of("Dimension:"), textRenderer);
            this.dimensionLabel.setPosition(190, 16);
            this.dimension = new RecustomTextFieldWidget(textRenderer, 240, 14, 75, 10, null);
            this.dimension.setMaxLength(100);
            this.dimension.setText(importer.getDimensionName());
            this.dimension.setTypedChangeListener((text) -> updateImporter());

            String text = importer.getImportType().name();
            int maxTextWidth = 48;
            int textWidth = textRenderer.getWidth(text);
            if (textWidth > maxTextWidth) {
                text = "..." + textRenderer.trimToWidth(text, maxTextWidth - 6);
            }
            this.importType = new TextWidget(0, 26, maxTextWidth, 8, Text.of(text), textRenderer);

            text = importer.getImportPath().toString();
            maxTextWidth = 268;
            textWidth = textRenderer.getWidth(text);
            if (textWidth > maxTextWidth) {
                text = "..." + textRenderer.trimToWidth(text, maxTextWidth - 6, true);
            }
            this.importPath = new TextWidget(50, 26, maxTextWidth, 8, Text.of(text), textRenderer);

            // TODO tooltips

            childListBecauseINeedToMakeItMyself.add(enabled);
            childListBecauseINeedToMakeItMyself.add(serverType);
            childListBecauseINeedToMakeItMyself.add(usesDefaultData);
            childListBecauseINeedToMakeItMyself.add(portLabel);
            childListBecauseINeedToMakeItMyself.add(port);
            childListBecauseINeedToMakeItMyself.add(worldLabel);
            childListBecauseINeedToMakeItMyself.add(world);
            childListBecauseINeedToMakeItMyself.add(dimensionLabel);
            childListBecauseINeedToMakeItMyself.add(dimension);
            childListBecauseINeedToMakeItMyself.add(importType);
            childListBecauseINeedToMakeItMyself.add(importPath);

            setEnabled(importer.isManuallyAdded() || importer.isUsesKnownServer());
        }

        private void onEnabledClicked(ButtonWidget btn) {
            setEnabled(enabled.isToggled());
        }

        void setEnabled(boolean enabled) {
            int textColor = enabled ? 0xFFFFFF : 0x808080;
            if (enabled != this.enabled.isToggled()) {
                this.enabled.setToggled(enabled);
            }
            for (ClickableWidget clickable : childListBecauseINeedToMakeItMyself) {
                if (clickable != this.enabled) {
                    clickable.active = enabled;
                }
                if (clickable instanceof TextWidget text) {
                    text.setTextColor(textColor);
                }
                if (clickable instanceof TextFieldWidget text) {
                    text.setEditable(enabled);
                }
            }
            this.importType.setTextColor(0x808080);
            this.importPath.setTextColor(0x606060);
        }

        private void updateImporter() {
            importer.setServerType(serverType.getCurrentValue());
            try {
                importer.setServerPort(Integer.parseInt(port.getText()));
            } catch (NumberFormatException ignored) {}
            importer.setWorldName(world.getText().toLowerCase(Locale.ROOT));
            importer.setDimensionName(world.getText().toLowerCase(Locale.ROOT));
            importer.setUsesDefaultDatapacks(usesDefaultData.isToggled());
        }

        @Override
        public Text getNarration() {
            return Text.of(String.format("%s port %d %s %s", importer.getServerName(), importer.getServerPort(), importer.getWorldName(), importer.getDimensionName()));
        }
    }
}
