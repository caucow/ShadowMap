package com.caucraft.shadowmap.client.gui.importer;

import com.caucraft.shadowmap.client.gui.component.RecustomIconButtonWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomToggleButtonWidget;
import com.caucraft.shadowmap.client.importer.ImportManager;
import com.caucraft.shadowmap.client.importer.ImportTask;
import com.caucraft.shadowmap.client.util.TextHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class ImportListWidget extends AlwaysSelectedEntryListWidget<ImportListWidget.StupidAbstraction> {
    private final ImportManager importManager;

    public ImportListWidget(MinecraftClient minecraftClient, ImportManager importManager, int width, int height, int top, int bottom) {
        super(minecraftClient, width, height, top, bottom, 40);
        this.importManager = importManager;
    }

    public void setSize(int width, int height) {
        int bottomDiff = this.bottom - this.height;
        updateSize(width, height, top, bottomDiff + height);
    }

    public void tick() {
        ImportTask<?> currentTask = importManager.getCurrentTask();
        for (StupidAbstraction entry : children()) {
            entry.tick(currentTask);
        }
    }

    public void refreshTasks() {
        clearEntries();
        for (ImportTask<?> task : importManager.getTasks()) {
            addEntry(new ImportEntry(task));
        }
    }

    public void setAllPaused(boolean paused) {
        for (StupidAbstraction entry : children()) {
            if (entry instanceof ImportEntry importEntry) {
                importEntry.task.setPaused(paused);
            }
        }
    }

    public void removeSelected() {
        StupidAbstraction entry = getSelectedOrNull();
        if (entry instanceof ImportEntry importEntry) {
            importManager.removeTask(importEntry.task.getId());
            removeEntry(entry);
        }
    }

    public void removeAll(boolean includeUnfinished) {
        for (StupidAbstraction entry : new ArrayList<>(children())) {
            if (entry instanceof ImportEntry importEntry) {
                ImportTask<?> task = importEntry.task;
                if (task.isDone() || includeUnfinished) {
                    importManager.removeTask(task.getId());
                    removeEntry(entry);
                }
            }
        }
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

        abstract void tick(ImportTask<?> currentTask);

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            setSelected(this);
            int x = getRowLeft();
            int y = getRowTop(ImportListWidget.this.children().indexOf(this));
            for (ClickableWidget widget : childListBecauseINeedToMakeItMyself) {
                widget.mouseClicked(mouseX - x, mouseY - y, button);
            }
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            int x = getRowLeft();
            int y = getRowTop(ImportListWidget.this.children().indexOf(this));
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

    class ImportEntry extends StupidAbstraction {
        final ImportTask<?> task;
        private float progress;
        private String progressString;
        private String worldString;
        private String pathString;
        private String typeString;

        private final RecustomIconButtonWidget setCurrentButton;
        private final RecustomToggleButtonWidget pauseButton;

        public ImportEntry(ImportTask<?> task) {
            this.task = task;
            this.progress = getNewProgress(task);
            this.progressString = getProgressString(progress);

            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

            String text = task.getWorldKey().toString();
            int maxTextWidth = 316;
            int textWidth = textRenderer.getWidth(text);
            if (textWidth > maxTextWidth) {
                text = "..." + textRenderer.trimToWidth(text, maxTextWidth - 6, true);
            }
            this.worldString = text;

            text = task.getImportFile().toString();
            maxTextWidth = 316;
            textWidth = textRenderer.getWidth(text);
            if (textWidth > maxTextWidth) {
                text = "..." + textRenderer.trimToWidth(text, maxTextWidth - 6, true);
            }
            this.pathString = text;

            this.typeString = task.getType().name();

            this.setCurrentButton = new RecustomIconButtonWidget(0, 0, 75, 12, "Set Current", this::onSetCurrentClicked);
            this.pauseButton = new RecustomToggleButtonWidget(75, 0, 75, 12, "Paused", this::onPauseClicked, task.isPaused());

            childListBecauseINeedToMakeItMyself.add(setCurrentButton);
            childListBecauseINeedToMakeItMyself.add(pauseButton);
        }

        void tick(ImportTask<?> currentTask) {
            float newProgress = getNewProgress(task);
            if (progress != newProgress) {
                progress = newProgress;
                progressString = getProgressString(newProgress);
            }
            setCurrentButton.active = task != currentTask && newProgress != -3;
            pauseButton.active = newProgress != -3;
            boolean paused = task.isPaused();
            if (paused != pauseButton.isToggled()) {
                pauseButton.setToggled(paused);
            }
        }

        private void onSetCurrentClicked(ButtonWidget btn) {
            importManager.setCurrentTask(task.getId());
        }

        private void onPauseClicked(ButtonWidget btn) {
            task.setPaused(!task.isPaused());
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX,
                int mouseY, boolean hovered, float tickDelta) {
            MatrixStack matrices = context.getMatrices();
            super.render(context, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, tickDelta);
            matrices.push();
            matrices.translate(x, y, 0);
            TextHelper textHelper = TextHelper.get(matrices).immediate(false);
            textHelper.draw(progressString, 150, 2);
            textHelper.color(0xFF808080);
            textHelper.draw(worldString, 0, 14);
            textHelper.draw(pathString, 0, 24);
            textHelper.draw(typeString, 225, 2);
            textHelper.flushBuffers();
            matrices.pop();
        }

        @Override
        public Text getNarration() {
            return Text.of(""); // TODO
        }

        private static float getNewProgress(ImportTask<?> task) {
            if (task.isDone()) {
                return -3 - task.getErrorCount();
            } else if (!task.isReady()) {
                return -2;
            } else if (task.isInitializing()) {
                return -1;
            } else {
                return task.getProgress();
            }
        }

        private static String getProgressString(float progress) {
            if (progress < -3) {
                return "Done w/ " + (-3 - (int) progress) + " Err";
            }
            if (progress == -3) {
                return "Done";
            }
            if (progress == -2) {
                return "Not Started";
            }
            if (progress == -1) {
                return "Initializing";
            }
            return String.format("%2.02f%%", progress * 100);
        }
    }
}
