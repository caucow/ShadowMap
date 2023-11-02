package com.caucraft.shadowmap.client.gui.waypoint;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.gui.IconAtlas;
import com.caucraft.shadowmap.client.gui.Icons;
import com.caucraft.shadowmap.client.gui.LessPoopScreen;
import com.caucraft.shadowmap.client.gui.component.ConfirmDialogWidget;
import com.caucraft.shadowmap.client.util.TextHelper;
import com.caucraft.shadowmap.client.waypoint.Waypoint;
import com.caucraft.shadowmap.client.waypoint.WaypointFilter;
import com.caucraft.shadowmap.client.waypoint.WaypointGroup;
import com.caucraft.shadowmap.client.waypoint.WorldWaypointManager;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class WaypointListWidget extends AlwaysSelectedEntryListWidget<WaypointListWidget.WaypointEntry> {

    private static final Comparator<Waypoint> WAYPOINT_COMPARATOR = (a, b) -> {
        int c = a.getName().compareToIgnoreCase(b.getName());
        if (c != 0) {
            return c;
        }
        c = Long.compare(a.getCreated(), b.getCreated());
        return c;
    };

    private final LessPoopScreen containingScreen;
    private final WorldWaypointManager waypointManager;
    private final boolean addControls;
    private PointControl pointDragControl;
    private GroupControl groupDragControl;
    private WaypointEntry draggedEntry;
    private final Set<WaypointEntry> draggedEntries;
    private List<OrderedText> description;
    private Consumer<Waypoint> onSelected;

    public WaypointListWidget(MinecraftClient client, WorldWaypointManager waypointManager, LessPoopScreen containingScreen,
            int width, int height, boolean addControls) {
        super(client, width, height, 40, height - 70, 18);
        this.containingScreen = containingScreen;
        this.waypointManager = waypointManager;
        this.addControls = addControls;
        draggedEntries = new ObjectOpenCustomHashSet<>(new Hash.Strategy<>() {
            @Override
            public int hashCode(WaypointEntry o) {
                return System.identityHashCode(o);
            }

            @Override
            public boolean equals(WaypointEntry a, WaypointEntry b) {
                return a == b;
            }
        });
        updateEntries();
    }

    public void setSize(int width, int height) {
        updateSize(width, height, 40, height - 70);
    }

    public void setSelectHandler(Consumer<Waypoint> onSelected) {
        this.onSelected = onSelected;
    }

    @Override
    public void setSelected(@Nullable WaypointListWidget.WaypointEntry selected) {
        super.setSelected(selected);
        if (onSelected == null) {
            return;
        }
        if (selected == null) {
            onSelected.accept(null);
            return;
        }
        onSelected.accept(selected.waypoint);
    }

    private void updateEntries() {
        double scroll = getScrollAmount();
        clearEntries();
        setSelected(null);
        if (waypointManager == null) {
            return;
        }
        for (WaypointEntry entry : getEntries(waypointManager.getRootWaypoints())) {
            addEntry(entry);
        }
        setScrollAmount(scroll);
    }

    private List<WaypointEntry> getEntries(Collection<Waypoint> waypointList) {
        List<Waypoint> tempList = new ArrayList<>(waypointList);
        List<WaypointEntry> entryList = new ArrayList<>();
        tempList.sort(WAYPOINT_COMPARATOR);
        Deque<WaypointEntry> addQueue = new ArrayDeque<>();
        Deque<WaypointListWidget.WaypointEntry> depthStack = new ArrayDeque<>();
        for (Waypoint waypoint : tempList) {
            addQueue.addLast(new WaypointEntry(waypoint, 0));
        }
        tempList.clear();
        int lastDepth = 0;
        while (!addQueue.isEmpty()) {
            WaypointListWidget.WaypointEntry next = addQueue.poll();
            entryList.add(next);
            while (next.depth < lastDepth) {
                lastDepth--;
                WaypointListWidget.WaypointEntry sibling = depthStack.pop();
                WaypointListWidget.WaypointEntry parent = depthStack.peek();
                if (parent != null) {
                    parent.children += sibling.children;
                }
            }
            lastDepth = next.depth;
            WaypointListWidget.WaypointEntry parent = depthStack.peek();
            if (parent != null) {
                parent.children++;
            }
            if (next.waypoint instanceof WaypointGroup group && group.isUiExpanded()) {
                tempList.addAll(group.getChildren());
                tempList.sort(WAYPOINT_COMPARATOR);
                int newDepth = next.depth + 1;
                for (int i = tempList.size() - 1; i >= 0; i--) {
                    addQueue.addFirst(new WaypointEntry(tempList.get(i), newDepth));
                }
                lastDepth = newDepth;
                tempList.clear();
                depthStack.push(next);
            }
        }
        while (!depthStack.isEmpty()) {
            WaypointListWidget.WaypointEntry sibling = depthStack.pop();
            WaypointListWidget.WaypointEntry parent = depthStack.peek();
            if (parent != null) {
                parent.children += sibling.children;
            }
        }
        return entryList;
    }

    @Override
    public int getRowWidth() {
        return 320;
    }

    @Override
    protected int getScrollbarPositionX() {
        return width / 2 + 164;
    }

    @Override
    public int addEntry(WaypointEntry entry) {
        return super.addEntry(entry);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        WaypointEntry dragged = draggedEntry;
        WaypointEntry hovered = getHoveredEntry();
        super.mouseReleased(mouseX, mouseY, button);
        if (hovered == dragged || dragged == null) {
            return true;
        }
        if (hovered == null) {
            tryAddToGroup(dragged.waypoint, null);
            return true;
        }
        Waypoint hoveredWaypoint = hovered.waypoint;
        if (hoveredWaypoint instanceof WaypointGroup group) {
            tryAddToGroup(dragged.waypoint, group);
            return true;
        }
        IconAtlas atlas = ShadowMap.getInstance().getIconAtlas();
        containingScreen.confirmAction("Convert waypoint \"" + hoveredWaypoint.getName() + "\" to a group?",
                new ConfirmDialogWidget.Option("OK", atlas.getIcon(Icons.EXPAND_ON), false, () -> {
                    if (tryAddToGroup(dragged.waypoint, hoveredWaypoint)) {
                        // Get waypoint fresh from waypoint manager since it will have been converted to a group.
                        client.setScreen(new EditWaypointScreen(containingScreen, waypointManager,
                                waypointManager.getWaypoint(hoveredWaypoint.getId()).get()));
                    }
                }),
                new ConfirmDialogWidget.Option("Cancel", atlas.getIcon(Icons.NO_CIRCLE), false, () -> {}));
        return true;
    }

    private boolean tryAddToGroup(Waypoint waypoint, Waypoint group) {
        try {
            waypointManager.addOrMoveWaypoint(waypoint, group == null ? null : group.getId());
            updateEntries();
            return true;
        } catch (IllegalArgumentException ex) {
            notifyException("Could not add waypoint \"" + waypoint.getName() + "\" to group \"" + group.getName() + '\"');
            return false;
        }
    }

    private void notifyException(String failMessage) {
        containingScreen.confirmAction(failMessage, new ConfirmDialogWidget.Option("OK", null, false, () -> {}));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrices = context.getMatrices();
        description = null;
        super.render(context, mouseX, mouseY, delta);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.getBuffer();
        if (description != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            int x1 = (width >> 1) - (getRowWidth() >> 1) - 20;
            int x2 = x1 + getRowWidth() + 40;
            int y1 = bottom + 2;
            int y2 = bottom + 8 + description.size() * 10;
            buffer.vertex(x1, y1, 100).color(0, 0, 0, 128).next();
            buffer.vertex(x1, y2, 100).color(0, 0, 0, 128).next();
            buffer.vertex(x2, y2, 100).color(0, 0, 0, 128).next();
            buffer.vertex(x2, y1, 100).color(0, 0, 0, 128).next();
            tess.draw();
            matrices.push();
            matrices.translate(0, 0, 100);
            TextHelper textHelper = TextHelper.get(matrices).immediate(false);
            for (int i = 0; i < description.size(); i++) {
                textHelper.draw(description.get(i), x1 + 22, y1 + 4 + i * 10);
            }
            textHelper.flushBuffers();
            matrices.pop();
        }
        if (isDragging() && draggedEntry != null) {
            Waypoint waypoint = draggedEntry.waypoint;
            int rgb = waypoint.getColorRGB();
            int r = rgb >> 16 & 0xFF;
            int g = rgb >> 8 & 0xFF;
            int b = rgb & 0xFF;
            String name = waypoint.getName();
            int nameWidth = client.textRenderer.getWidth(name);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            int x1 = mouseX - 4;
            int x2 = mouseX + 5 + nameWidth;
            int y1 = mouseY - 5;
            int y2 = mouseY + 5;
            buffer.vertex(x1, y1, 0).color(0, 0, 0, 128).next();
            buffer.vertex(x1, y2, 0).color(0, 0, 0, 128).next();
            buffer.vertex(x2, y2, 0).color(0, 0, 0, 128).next();
            buffer.vertex(x2, y1, 0).color(0, 0, 0, 128).next();
            x1++;
            x2 = mouseX + 3;
            y1 = mouseY - 3;
            y2 = mouseY + 3;
            buffer.vertex(x1, y1, 0).color(r, g, b, 255).next();
            buffer.vertex(x1, y2, 0).color(r, g, b, 255).next();
            buffer.vertex(x2, y2, 0).color(r, g, b, 255).next();
            buffer.vertex(x2, y1, 0).color(r, g, b, 255).next();
            tess.draw();
            TextHelper.get(matrices).color(0x80FFFFFF).draw(name, mouseX + 4, mouseY - 4);
        }
    }

    static void drawSprite(BufferBuilder buffer, int x, int y, int w, int h, Sprite sprite, int grayLevel) {
        buffer.vertex(x, y, 2).color(grayLevel, grayLevel, grayLevel, 255).texture(sprite.getMinU(), sprite.getMinV()).next();
        buffer.vertex(x, y + w, 2).color(grayLevel, grayLevel, grayLevel, 255).texture(sprite.getMinU(), sprite.getMaxV()).next();
        buffer.vertex(x + h, y + w, 2).color(grayLevel, grayLevel, grayLevel, 255).texture(sprite.getMaxU(), sprite.getMaxV()).next();
        buffer.vertex(x + h, y, 2).color(grayLevel, grayLevel, grayLevel, 255).texture(sprite.getMaxU(), sprite.getMinV()).next();
    }

    class WaypointEntry extends Entry<WaypointEntry> {
        final Waypoint waypoint;
        int depth;
        int children;

        WaypointEntry(Waypoint waypoint, int depth) {
            this.waypoint = waypoint;
            this.depth = depth;
        }

        private void updateEntry() {
            updateEntries();
        }

        private void editWaypoint() {
            client.setScreen(new EditWaypointScreen(containingScreen, waypointManager, waypoint));
        }

        private void delete() {
            IconAtlas atlas = ShadowMap.getInstance().getIconAtlas();
            String prompt;
            ConfirmDialogWidget.Option[] options;
            if (waypoint instanceof WaypointGroup group) {
                prompt = "Are you sure you want to delete this group?";
                options = new ConfirmDialogWidget.Option[] {
                        new ConfirmDialogWidget.Option("Keep Contents", atlas.getIcon(Icons.DELETE_GROUP_NOT_CHILDREN), false, () -> {
                            waypointManager.removeWaypoint(waypoint, true);
                            updateEntries();
                        }),
                        new ConfirmDialogWidget.Option("Delete Contents", atlas.getIcon(Icons.DELETE_GROUP_AND_CHILDREN), false, () -> {
                            waypointManager.removeWaypoint(waypoint, false);
                            updateEntries();
                        }),
                        new ConfirmDialogWidget.Option("Cancel", atlas.getIcon(Icons.NO_CIRCLE), false, () -> {})
                };
            } else {
                prompt = "Are you sure you want to delete this waypoint?";
                options = new ConfirmDialogWidget.Option[] {
                        new ConfirmDialogWidget.Option("Yes", atlas.getIcon(Icons.TRASH), false, () -> {
                            waypointManager.removeWaypoint(waypoint, true);
                            updateEntries();
                        }),
                        new ConfirmDialogWidget.Option("Cancel", atlas.getIcon(Icons.NO_CIRCLE), false, () -> {})
                };
            }
            String name = waypoint.getName();
            if (name != null) {
                prompt += "\n\"" + name + '"';
            }
            containingScreen.confirmAction(prompt, options);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            setSelected(this);
            SoundManager soundManager = MinecraftClient.getInstance().getSoundManager();
            int mx = (int) (mouseX - getRowLeft());
            int entryWidth = getRowWidth() - 4;
            if (!addControls) {
                int ctrlX = GroupControl.UI_EXPAND.getXPos(this, getRowWidth() - 4);
                if (waypoint instanceof WaypointGroup group && mx >= ctrlX && mx <= ctrlX + 14) {
                    GroupControl.UI_EXPAND.onClick(this, group);
                    soundManager.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                }
                return true;
            }
            pointDragControl = null;
            groupDragControl = null;
            draggedEntries.clear();
            for (PointControl control : PointControl.values()) {
                int ctrlX = control.getXPos(this, entryWidth);
                if (mx >= ctrlX && mx <= ctrlX + 14) {
                    control.onClick(this, waypoint);
                    soundManager.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    pointDragControl = control.isDraggable ? control : null;
                    draggedEntries.add(this);
                    return true;
                }
            }
            if (waypoint instanceof WaypointGroup group) {
                for (GroupControl control : GroupControl.values()) {
                    int ctrlX = control.getXPos(this, entryWidth);
                    if (mx >= ctrlX && mx <= ctrlX + 14) {
                        control.onClick(this, group);
                        soundManager.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                        groupDragControl = control.isDraggable ? control : null;
                        draggedEntries.add(this);
                        return true;
                    }
                }
            }
            draggedEntry = this;
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            pointDragControl = null;
            groupDragControl = null;
            draggedEntries.clear();
            draggedEntry = null;
            return true;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            WaypointEntry hovered = getHoveredEntry();
            if (hovered == null) {
                return false;
            }
            if (pointDragControl != null && draggedEntries.add(hovered)) {
                pointDragControl.onClick(this, hovered.waypoint);
                SoundManager soundManager = MinecraftClient.getInstance().getSoundManager();
                soundManager.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                return true;
            }
            if (groupDragControl != null && draggedEntries.add(hovered) && hovered.waypoint instanceof WaypointGroup group) {
                groupDragControl.onClick(this, group);
                SoundManager soundManager = MinecraftClient.getInstance().getSoundManager();
                soundManager.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX,
                int mouseY, boolean hovered, float tickDelta) {
            entryWidth = getRowWidth() - 4;
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            int indent = (depth * 8) + 17;
            renderColorSquareAndGroup(x, y, entryWidth, entryHeight, indent, hovered);
            renderName(context, x, y, entryWidth, entryHeight, indent);
            renderButtonIcons(context, x, y, entryWidth, hovered, mouseX);
        }

        private void renderColorSquareAndGroup(int x, int y, int entryWidth, int entryHeight, int indent, boolean hovered) {
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buff = tess.getBuffer();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            int rgb = waypoint.getColorRGB();
            int r = rgb >> 16 & 0xFF;
            int g = rgb >> 8 & 0xFF;
            int b = rgb & 0xFF;
            int a = hovered ? 128 : 64;
            buff.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            int x1 = x;
            int x2 = x1 + entryWidth;
            int y1 = y;
            int y2 = y + entryHeight;
            buff.vertex(x1, y1, 0).color(64, 64, 64, a).next();
            buff.vertex(x1, y2, 0).color(64, 64, 64, a).next();
            buff.vertex(x2, y2, 0).color(64, 64, 64, a).next();
            buff.vertex(x2, y1, 0).color(64, 64, 64, a).next();
            if (children > 0 || waypoint instanceof WaypointGroup) {
                x1 = x + indent + 32;
                x2 = x + indent + 34;
                y1 = y + (entryHeight >> 1);
                y2 = y + entryHeight + itemHeight * children - 1;
                buff.vertex(x1 - 1, y1 + 0, 1).color(255, 255, 255, 255).next();
                buff.vertex(x1 - 1, y2 + 1, 1).color(255, 255, 255, 255).next();
                buff.vertex(x2 + 1, y2 + 1, 1).color(255, 255, 255, 255).next();
                buff.vertex(x2 + 1, y1 + 0, 1).color(255, 255, 255, 255).next();
                buff.vertex(x1, y1, 1).color(r, g, b, 255).next();
                buff.vertex(x1, y2, 1).color(r, g, b, 255).next();
                buff.vertex(x2, y2, 1).color(r, g, b, 255).next();
                buff.vertex(x2, y1, 1).color(r, g, b, 255).next();
            }
            x1 = x + indent + 28;
            x2 = x + indent + 38;
            y1 = y + (entryHeight >> 1) - 5;
            y2 = y + (entryHeight >> 1) + 5;
            buff.vertex(x1, y1, 1).color(255, 255, 255, 255).next();
            buff.vertex(x1, y2, 1).color(255, 255, 255, 255).next();
            buff.vertex(x2, y2, 1).color(255, 255, 255, 255).next();
            buff.vertex(x2, y1, 1).color(255, 255, 255, 255).next();
            buff.vertex(x1 + 1, y1 + 1, 1).color(r, g, b, 255).next();
            buff.vertex(x1 + 1, y2 - 1, 1).color(r, g, b, 255).next();
            buff.vertex(x2 - 1, y2 - 1, 1).color(r, g, b, 255).next();
            buff.vertex(x2 - 1, y1 + 1, 1).color(r, g, b, 255).next();
            tess.draw();
        }

        private void renderName(DrawContext context, int x, int y, int entryWidth, int entryHeight, int indent) {
            MatrixStack matrices = context.getMatrices();
            int color = 0xfefefe;
            if (!waypoint.isVisible()) {
                color >>>= 1;
            }
            if (waypoint.isHighlighted()) {
                color &= 0xFFFF00;
            }
//            if (waypoint.isTemporary()) {
//                color &= 0xFF0000;
//            }
            int maxStringWidth = entryWidth - entryHeight - indent - 27;
            String name = waypoint.getName();
            if (client.textRenderer.getWidth(name) > maxStringWidth) {
                name = client.textRenderer.trimToWidth(name, maxStringWidth - 6) + "...";
            }
            TextHelper.get(matrices).color(0xFF000000 | color).draw(name, x + entryHeight + indent + 27, y + 3);
        }

        private void renderButtonIcons(DrawContext drawContext, int x, int y, int entryWidth, boolean hovered, int mouseX) {
            /*
            Controls:
                Waypoint:
                    - Drag-to-Group
                    - Teleport
                    - Share/Copy
                Group:
                    - Expand/Collapse UI
             */

            ShadowMap map = ShadowMap.getInstance();
            IconAtlas atlas = map.getIconAtlas();
            RenderSystem.setShaderTexture(0, IconAtlas.TEX_ID);
            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buffer = tess.getBuffer();
            Waypoint waypoint = this.waypoint;

            if (hovered && addControls) {
                int minCtrlX = entryWidth - 4;
                for (PointControl control : PointControl.values()) {
                    int ctrlX = control.getXPos(this, entryWidth);
                    if (ctrlX > 50 && ctrlX < minCtrlX) {
                        minCtrlX = ctrlX;
                    }
                }
                if (waypoint instanceof WaypointGroup) {
                    for (GroupControl control : GroupControl.values()) {
                        int ctrlX = control.getXPos(this, entryWidth);
                        if (ctrlX > 50 && ctrlX < minCtrlX) {
                            minCtrlX = ctrlX;
                        }
                    }
                }
                minCtrlX -= 10;

                RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                buffer.vertex(x + minCtrlX, y, 2).color(0, 0, 0, 192).next();
                buffer.vertex(x + minCtrlX, y + 14, 2).color(0, 0, 0, 192).next();
                buffer.vertex(x + entryWidth - 4, y + 14, 2).color(0, 0, 0, 192).next();
                buffer.vertex(x + entryWidth - 4, y, 2).color(0, 0, 0, 192).next();
                tess.draw();

                RenderSystem.setShader(GameRenderer::getPositionColorTexProgram);
                buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE);
                for (PointControl control : PointControl.values()) {
                    Sprite icon = control.getIcon(waypoint, atlas);
                    int x1 = x + control.getXPos(this, entryWidth);
                    int grayLevel = 192;
                    if (mouseX >= x1 && mouseX <= x1 + 13) {
                        grayLevel = 255;
                        description = client.textRenderer.wrapLines(Text.of(control.getDescription(waypoint)), getRowWidth());
                    }
                    drawSprite(buffer, x + control.getXPos(this, entryWidth), y, 14, 14, icon, grayLevel);
                }
                if (waypoint instanceof WaypointGroup group) {
                    for (GroupControl control : GroupControl.values()) {
                        Sprite icon = control.getIcon(group, atlas);
                        int x1 = x + control.getXPos(this, entryWidth);
                        int grayLevel = 192;
                        if (mouseX >= x1 && mouseX <= x1 + 13) {
                            grayLevel = 255;
                            description = client.textRenderer.wrapLines(Text.of(control.getDescription(group)), getRowWidth() - 4);
                        }
                        drawSprite(buffer, x + control.getXPos(this, entryWidth), y, 14, 14, icon, grayLevel);
                    }
                }
                tess.draw();
            } else {
                RenderSystem.setShader(GameRenderer::getPositionColorTexProgram);
                buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE);
                int grayLevel = 128;
                int x1 = x + PointControl.VISIBLE.getXPos(this, entryWidth);
                Sprite icon = PointControl.VISIBLE.getIcon(waypoint, atlas);
                drawSprite(buffer, x1, y, 14, 14, icon, grayLevel);
                if (waypoint.isHighlighted()) {
                    x1 = x + PointControl.HIGHLIGHT.getXPos(this, entryWidth);
                    icon = PointControl.HIGHLIGHT.getIcon(waypoint, atlas);
                    drawSprite(buffer, x1, y, 14, 14, icon, grayLevel);
                }
                if (waypoint instanceof WaypointGroup group) {
                    x1 = x + GroupControl.EXPAND.getXPos(this, entryWidth);
                    icon = GroupControl.EXPAND.getIcon(group, atlas);
                    drawSprite(buffer, x1, y, 14, 14, icon, grayLevel);
                    x1 = x + GroupControl.UI_EXPAND.getXPos(this, entryWidth);
                    if (hovered && mouseX >= x1 && mouseX <= x1 + 14) {
                        grayLevel = 255;
                    }
                    icon = GroupControl.UI_EXPAND.getIcon(group, atlas);
                    drawSprite(buffer, x1, y, 14, 14, icon, grayLevel);
                }
                tess.draw();
            }

        }

        @Override
        public Text getNarration() {
            return Text.of(waypoint.getName());
        }
    }

    private enum PointControl {
        HIGHLIGHT(0, false,
                (wp) -> wp.isHighlighted() ? Icons.HIGHLIGHTER_ON : Icons.HIGHLIGHTER_OFF,
                (entry, wp) -> {
                    WorldWaypointManager wpManager = wp.getWaypointManager();
                    wpManager.setHighlighted(wp, !wp.isHighlighted());
                },
                (wp) -> "Waypoint " + (wp.getWaypointManager().isHighlighted(wp) ? "highlighted" : "not highlighted") + ".\nToggle a highlight on this waypoint, bypassing all filters and visibility settings and highlighting it on the map and in world."),
        VISIBLE(15, true,
                (wp) -> wp.isVisible() ? Icons.VISIBLE_ON : Icons.VISIBLE_OFF,
                (entry, wp) -> wp.setVisible(!wp.isVisible()),
                (wp) -> "Waypoint " + (wp.isVisible() ? "visible" : "hidden") + ".\nToggle this waypoint's visibility. If this is a group and hidden, contained waypoints and subgroups are also hidden."),
        VISIBLE_FILTER(-70, true,
                (wp) -> wp.getVisibleFilter() == null ? Icons.VISIBLE_FILTER_OFF : wp.getVisibleFilter().isEnabled() ? Icons.VISIBLE_FILTER_ON : Icons.VISIBLE_FILTER_OFF,
                (entry, wp) -> {
                    WaypointFilter filter = wp.getVisibleFilter();
                    if (filter != null) {
                        filter.setEnabled(!filter.isEnabled());
                    }
                },
                (wp) -> "Waypoint visibility filter " + (wp.getVisibleFilter() == null ? "missing" : wp.getVisibleFilter().isEnabled() ? "enabled" : "disabled") + ".\nAdding a visibility filter to a waypoint will hide it when the player moves too far away. With the filter enabled, the waypoint must both be set as visible AND the player must be nearby for the waypoint to be visible. Edit the waypoint to configure filters."),
        EDIT(-55, false,
                (wp) -> Icons.EDIT_ON,
                (entry, wp) -> entry.editWaypoint(),
                (wp) -> "Edit waypoint. This allows more fine-grained control over waypoint settings, including adding and changing filters."),
//        TEMPORARY(-35, false,
//                (wp) -> wp.isTemporary() ? Icons.STOPWATCH_ON : Icons.STOPWATCH_OFF,
//                (entry, wp) -> wp.setTemporary(!wp.isTemporary()),
//                (wp) -> "Waypoint " + (wp.isTemporary() ? "temporary" : "permanent") + ".\nSet this waypoint as either a temporary or permanent waypoint. Temporary waypoints are lost when the world is unloaded. This may happen any time between disconnecting from/leaving the world (including changing dimensions) and closing the game."),
        DELETE(-20, false,
                (wp) -> Icons.TRASH,
                (entry, wp) -> entry.delete(),
                (wp) -> "Delete this waypoint. If this is a group, optionally delete all contained waypoints or shift them to the group above.");

        private int xPos;
        private boolean isDraggable;
        final private Function<Waypoint, Icons> getIcon;
        final private BiConsumer<WaypointEntry, Waypoint> onClick;
        final private Function<Waypoint, String> getDescription;

        PointControl(int xPos, boolean isDraggable, Function<Waypoint, Icons> getIcon, BiConsumer<WaypointEntry, Waypoint> onClick, Function<Waypoint, String> getDescription) {
            this.xPos = xPos;
            this.isDraggable = isDraggable;
            this.getIcon = getIcon;
            this.onClick = onClick;
            this.getDescription = getDescription;
        }

        int getXPos(WaypointEntry entry, int entryWidth) {
            return xPos >= 0 ? xPos : entryWidth + xPos;
        }

        Sprite getIcon(Waypoint waypoint, IconAtlas atlas) {
            return atlas.getIcon(getIcon.apply(waypoint));
        }

        void onClick(WaypointEntry entry, Waypoint waypoint) {
            onClick.accept(entry, waypoint);
        }

        String getDescription(Waypoint waypoint) {
            return getDescription.apply(waypoint);
        }
    }

    private enum GroupControl {
        EXPAND(30, true,
                (wp) -> wp.isExpanded() ? Icons.EXPAND_ON : Icons.EXPAND_OFF,
                (entry, wp) -> wp.setExpanded(!wp.isExpanded()),
                (wp) -> "Group " + (wp.isExpanded() ? "expanded" : "collapsed") + ".\nToggle whether to show waypoints and subgroups in this group, or just this group."),
        UI_EXPAND(43, false,
                (wp) -> wp.isUiExpanded() ? Icons.MINUS : Icons.PLUS,
                (entry, wp) -> {
                    wp.setUiExpanded(!wp.isUiExpanded());
                    entry.updateEntry();
                },
                (wp) -> "Group list-" + (wp.isUiExpanded() ? "expanded" : "collapsed") + ".\nToggle whether to show waypoints and subgroups in the waypoint list."),
        EXPAND_FILTER(-140, true,
                (wp) -> wp.getExpandFilter() == null ? Icons.EXPAND_FILTER_OFF : wp.getExpandFilter().isEnabled() ? Icons.EXPAND_FILTER_ON : Icons.EXPAND_FILTER_OFF,
                (entry, wp) -> {
                    WaypointFilter filter = wp.getExpandFilter();
                    if (filter != null) {
                        filter.setEnabled(!filter.isEnabled());
                    }
                },
                (wp) -> "Waypoint expand filter " + (wp.getExpandFilter() == null ? "missing" : wp.getExpandFilter().isEnabled() ? "enabled" : "disabled") + ".\nAdding an expand filter to a group will collapse it when the player moves too far away. With the filter enabled, the group must both be set as expanded AND the player must be nearby for the group to be expanded. Edit the waypoint to configure filters."),
        AUTORESIZE(-125, true,
                (wp) -> wp.isAutoResize() ? Icons.AUTORESIZE_ON : Icons.AUTORESIZE_OFF,
                (entry, wp) -> wp.setAutoResize(!wp.isAutoResize(), true),
                (wp) -> "Group " + (wp.isAutoResize() ? "" : "not ") + "auto-resizeable.\nAuto-resizing groups adjust their location and filter radii to fit their contents. Edit the group to configure filters and filter auto-buffers."),
        DRAW_COLLAPSE(-110, false,
                (wp) -> switch (wp.getDrawCollapsed()) {
                    case NONE -> Icons.DRAW_COLLAPSE_NONE;
                    case FILTER -> Icons.DRAW_COLLAPSE_FILTER;
                    case POINT -> Icons.DRAW_COLLAPSE_POINT;
                    case POINT_FILTER -> Icons.DRAW_COLLAPSE_POINT_FILTER;
                },
                (entry, wp) -> {
                    WaypointGroup.DrawMode[] values = WaypointGroup.DrawMode.values();
                    wp.setDrawCollapsed(values[(wp.getDrawCollapsed().ordinal() + 1) % values.length]);
                },
                (wp) -> "Collapsed draw mode: " + wp.getDrawCollapsed().name() + ".\nSet how the group appears when collapsed.\nNONE: Do not draw a waypoint.\nFILTER: Draw the group's expand-filter shape.\nPOINT: Draw the group like a normal waypoint.\nPOINT_FILTER: Draw both a waypoint and the filter."),
        DRAW_EXPAND(-95, false,
                (wp) -> switch (wp.getDrawExpanded()) {
                    case NONE -> Icons.DRAW_EXPAND_NONE;
                    case FILTER -> Icons.DRAW_EXPAND_FILTER;
                    case POINT -> Icons.DRAW_EXPAND_POINT;
                    case POINT_FILTER -> Icons.DRAW_EXPAND_POINT_FILTER;
                },
                (entry, wp) -> {
                    WaypointGroup.DrawMode[] values = WaypointGroup.DrawMode.values();
                    wp.setDrawExpanded(values[(wp.getDrawExpanded().ordinal() + 1) % values.length]);
                },
                (wp) -> "Expanded draw mode: " + wp.getDrawCollapsed().name() + ".\nSet how the group appears when expanded.\nNONE: Do not draw a waypoint.\nFILTER: Draw the group's expand-filter shape.\nPOINT: Draw the group like a normal waypoint.\nPOINT_FILTER: Draw both a waypoint and the filter.");

        private final int xPos;
        private final boolean isDraggable;
        private final Function<WaypointGroup, Icons> getIcon;
        private final BiConsumer<WaypointEntry, WaypointGroup> onClick;
        private final Function<WaypointGroup, String> getDescription;

        GroupControl(int xPos, boolean isDraggable, Function<WaypointGroup, Icons> getIcon, BiConsumer<WaypointEntry, WaypointGroup> onClick, Function<WaypointGroup, String> getDescription) {
            this.xPos = xPos;
            this.isDraggable = isDraggable;
            this.getIcon = getIcon;
            this.onClick = onClick;
            this.getDescription = getDescription;
        }

        int getXPos(WaypointEntry entry, int entryWidth) {
            if (this == UI_EXPAND) {
                return xPos + entry.depth * 8;
            }
            return xPos >= 0 ? xPos : entryWidth + xPos;
        }

        Sprite getIcon(WaypointGroup waypoint, IconAtlas atlas) {
            return atlas.getIcon(getIcon.apply(waypoint));
        }

        void onClick(WaypointEntry entry, WaypointGroup waypoint) {
            onClick.accept(entry, waypoint);
        }

        String getDescription(WaypointGroup waypoint) {
            return getDescription.apply(waypoint);
        }
    }
}
