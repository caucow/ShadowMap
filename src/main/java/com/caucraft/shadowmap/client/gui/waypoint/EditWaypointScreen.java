package com.caucraft.shadowmap.client.gui.waypoint;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.config.PrivacyConfig;
import com.caucraft.shadowmap.client.config.WaypointConfig;
import com.caucraft.shadowmap.client.gui.IconAtlas;
import com.caucraft.shadowmap.client.gui.Icons;
import com.caucraft.shadowmap.client.gui.LessPoopScreen;
import com.caucraft.shadowmap.client.gui.component.ColorSelectWidget;
import com.caucraft.shadowmap.client.gui.component.ConfirmDialogWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomCycleButtonWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomIconButtonWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomTextFieldWidget;
import com.caucraft.shadowmap.client.gui.component.RecustomToggleButtonWidget;
import com.caucraft.shadowmap.client.util.MapUtils;
import com.caucraft.shadowmap.client.waypoint.Waypoint;
import com.caucraft.shadowmap.client.waypoint.WaypointFilter;
import com.caucraft.shadowmap.client.waypoint.WaypointGroup;
import com.caucraft.shadowmap.client.waypoint.WorldWaypointManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Vector3d;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class EditWaypointScreen extends LessPoopScreen {

    private final Screen parentScreen;
    private final WorldWaypointManager waypointManager;
    private UUID waypointId;
    private boolean isGroup;
    private UUID parentId;
    private WaypointFilter visibleFilter;
    private WaypointFilter expandFilter;
    private boolean autoShortName;

    // Waypoint Controls
    private final TextWidget titleText;
    private final RecustomTextFieldWidget nameField;
    private final RecustomTextFieldWidget shortNameField;
    private final ColorSelectWidget colorSelect;
    private final RecustomTextFieldWidget colorField;
    private final TextWidget coordsPrivacyLabel;
    private final RecustomTextFieldWidget xField;
    private final RecustomTextFieldWidget yField;
    private final RecustomTextFieldWidget zField;
    private final RecustomToggleButtonWidget visibleToggle;
    private final RecustomToggleButtonWidget temporaryToggle;
    private final RecustomIconButtonWidget visibleFilterButton;
    private final RecustomIconButtonWidget groupButton;

    // Group Controls
    private TextWidget groupLabel;
    private RecustomToggleButtonWidget expandButton;
    private RecustomToggleButtonWidget autoResizeButton;
    private TextWidget drawModeLabel;
    private RecustomCycleButtonWidget<WaypointGroup.DrawMode> drawModeCollapsedButton;
    private RecustomCycleButtonWidget<WaypointGroup.DrawMode> drawModeExpandedButton;
    private TextWidget filterLabel;
    private RecustomIconButtonWidget expandFilterButton;
    private TextWidget filterBufferLabel;
    private RecustomTextFieldWidget visibleBufferField;
    private RecustomTextFieldWidget expandBufferField;

    private final RecustomIconButtonWidget deleteButton;
    private final RecustomIconButtonWidget saveButton;
    private final RecustomIconButtonWidget cancelButton;

    private EditWaypointScreen(Screen parentScreen, WorldWaypointManager waypointManager, UUID waypointId, String name, String shortName, Vector3d pos, int color, UUID parentId, boolean isGroup) {
        super(Text.of(getTitle(waypointManager, waypointId, isGroup)));
        this.parentScreen = parentScreen;
        this.waypointManager = waypointManager;
        this.waypointId = waypointId;
        this.parentId = parentId;
        this.isGroup = isGroup;

        WaypointConfig config = ShadowMap.getInstance().getConfig().waypointConfig;
        IconAtlas atlas = ShadowMap.getInstance().getIconAtlas();
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        nameField = new RecustomTextFieldWidget(textRenderer, 0, 0, 190, 20, Text.empty());
        shortNameField = new RecustomTextFieldWidget(textRenderer, 0, 0, 35, 20, Text.empty());
        colorSelect = new ColorSelectWidget(0, 0, 20, 20, color, this::colorSelected);
        colorField = new RecustomTextFieldWidget(textRenderer, 0, 0, 54, 20, Text.empty());
        coordsPrivacyLabel = new TextWidget(200, 16, Text.of("- Coords Hidden : Privacy Mode Enabled -"), textRenderer);
        xField = new RecustomTextFieldWidget(textRenderer, 0, 0, 76, 16, Text.empty());
        yField = new RecustomTextFieldWidget(textRenderer, 0, 0, 54, 16, Text.empty());
        zField = new RecustomTextFieldWidget(textRenderer, 0, 0, 76, 16, Text.empty());
        visibleToggle = new RecustomToggleButtonWidget(0, 0, 100, 20, "Visible", (btn) -> {}, true);
        temporaryToggle = new RecustomToggleButtonWidget(0, 0, 100, 20, "Temp", (btn) -> {}, false);
        visibleFilterButton = new RecustomIconButtonWidget(0, 0, 100, 20, "Visibility Filter", this::visibleFilterClicked);
        groupButton = new RecustomIconButtonWidget(0, 0, 200, 20, parentId == null ? "No Group" : waypointManager.getWaypoint(
                parentId).map((waypoint) -> "Group: " + waypoint.getName()).orElse("Unknown Group"), this::groupSelectClicked);

        deleteButton = new RecustomIconButtonWidget(0, 0, 75, 20, "Delete", this::deleteClicked);
        saveButton = new RecustomIconButtonWidget(0, 0, 75, 20, "Save", this::saveClicked);
        cancelButton = new RecustomIconButtonWidget(0, 0, 75, 20, "Cancel", this::cancelClicked);
        titleText = new TextWidget(getTitle(), textRenderer);

        nameField.setText(name);
        nameField.setMaxLength(64);

        shortNameField.setText(shortName);
        shortNameField.setMaxLength(4);

        autoShortName = shortName == null || shortName.isEmpty() || shortName.equals(Waypoint.getShortName(name));
        nameField.setTypedChangeListener((newText) -> {
            if (autoShortName) {
                shortNameField.setText(Waypoint.getShortName(newText));
            }
        });
        if (autoShortName) {
            shortNameField.setText(Waypoint.getShortName(name));
        }
        shortNameField.setTypedChangeListener((newText) -> {
            autoShortName = newText == null || newText.isEmpty() || newText.equals(Waypoint.getShortName(nameField.getText()));
        });

        xField.setTextPredicate(RecustomTextFieldWidget.DECIMAL_FILTER);
        xField.setText(String.format("%.3f", pos.x));
        yField.setTextPredicate(RecustomTextFieldWidget.DECIMAL_FILTER);
        yField.setText(String.format("%.3f", pos.y));
        zField.setTextPredicate(RecustomTextFieldWidget.DECIMAL_FILTER);
        zField.setText(String.format("%.3f", pos.z));

        visibleToggle.setIcons(atlas.getIcon(Icons.VISIBLE_OFF), atlas.getIcon(Icons.VISIBLE_ON));
        colorField.setText(String.format("%06x",  color & 0x00FFFFFF));
        colorField.setMaxLength(6);
        colorField.setTextPredicate((str) -> str.matches("[0-9a-fA-F]{0,6}"));
        colorField.setTypedChangeListener(this::colorEntered);

        temporaryToggle.setIcons(atlas.getIcon(Icons.STOPWATCH_OFF), atlas.getIcon(Icons.STOPWATCH_ON));
        visibleFilterButton.setIcon(atlas.getIcon(Icons.VISIBLE_FILTER_ON));

        deleteButton.setIcon(atlas.getIcon(Icons.TRASH));
        deleteButton.active = waypointId != null;
        saveButton.setIcon(atlas.getIcon(Icons.FLOPPY));
        cancelButton.setIcon(atlas.getIcon(Icons.NO_CIRCLE));
        titleText.setNavigationOrder(100);

        shortNameField.setTooltipDelay(1000);
        shortNameField.setTooltip(Tooltip.of(Text.of("Set short name or abbreviation for the map.")));
        Tooltip tip = Tooltip.of(Text.of("The waypoint's X/Y/Z coordinates"));
        xField.setTooltipDelay(1000);
        xField.setTooltip(tip);
        yField.setTooltipDelay(1000);
        yField.setTooltip(tip);
        zField.setTooltipDelay(1000);
        zField.setTooltip(tip);
        visibleToggle.setTooltipDelay(1000);
        visibleToggle.setTooltip(Tooltip.of(Text.of("Toggle this waypoint's visibility. If this is a group, also hides contents.")));
        temporaryToggle.setTooltipDelay(1000);
        temporaryToggle.setTooltip(Tooltip.of(Text.of("Set this waypoint as temporary or permanent. Temporary waypoints are lost when the map is unloaded.")));
        groupButton.setTooltipDelay(1000);
        groupButton.setTooltip(Tooltip.of(Text.of("Set the group this waypoint is in. If this is a group, its parent group cannot be set to itself or one of its subgroups.")));
        visibleFilterButton.setTooltipDelay(1000);
        visibleFilterButton.setTooltip(Tooltip.of(Text.of("Configure the waypoint's visibility filter. If this is enabled, the waypoint will be hidden when the player is too far.")));

        if (isGroup) {
            groupLabel = new TextWidget(Text.of("Group Settings"), textRenderer);
            expandButton = new RecustomToggleButtonWidget(0, 0, 150, 20, "Expand Contents", (btn) -> {}, true);
            autoResizeButton = new RecustomToggleButtonWidget(0, 0, 150, 20, "Auto-Position", (btn) -> {}, true);

            drawModeLabel = new TextWidget(Text.of("Draw Modes"), textRenderer);
            WaypointGroup.DrawMode[] drawModes = WaypointGroup.DrawMode.values();
            Sprite[] icons = {
                    atlas.getIcon(Icons.DRAW_COLLAPSE_NONE),
                    atlas.getIcon(Icons.DRAW_COLLAPSE_POINT),
                    atlas.getIcon(Icons.DRAW_COLLAPSE_FILTER),
                    atlas.getIcon(Icons.DRAW_COLLAPSE_POINT_FILTER)
            };
            drawModeCollapsedButton = new RecustomCycleButtonWidget<>(0, 0, 150, 20, "Collapsed", (btn) -> {}, drawModes, 1, icons);
            icons = new Sprite[] {
                    atlas.getIcon(Icons.DRAW_EXPAND_NONE),
                    atlas.getIcon(Icons.DRAW_EXPAND_POINT),
                    atlas.getIcon(Icons.DRAW_EXPAND_FILTER),
                    atlas.getIcon(Icons.DRAW_EXPAND_POINT_FILTER)
            };
            drawModeExpandedButton = new RecustomCycleButtonWidget<>(0, 0, 150, 20, "Expanded", (btn) -> {}, drawModes, 1, icons);

            filterLabel = new TextWidget(Text.of("Auto-Filters"), textRenderer);
            expandFilterButton = new RecustomIconButtonWidget(0, 0, 100, 20, "Expander Filter", this::expandFilterClicked);

            filterBufferLabel = new TextWidget(Text.of("AF Buffer"), textRenderer);
            visibleBufferField = new RecustomTextFieldWidget(textRenderer, 0, 0, 45, 16, Text.empty());
            expandBufferField = new RecustomTextFieldWidget(textRenderer, 0, 0, 45, 16, Text.empty());

            expandButton.setIcons(atlas.getIcon(Icons.EXPAND_OFF), atlas.getIcon(Icons.EXPAND_ON));
            autoResizeButton.setIcons(atlas.getIcon(Icons.AUTORESIZE_OFF), atlas.getIcon(Icons.AUTORESIZE_ON));
            expandFilterButton.setIcon(atlas.getIcon(Icons.EXPAND_FILTER_ON));

            int distance = config.defaultVisibleDistance.get();
            visibleBufferField.setText(Integer.toString(distance));
            visibleBufferField.setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);
            distance = config.defaultExpandDistance.get();
            expandBufferField.setText(Integer.toString(distance));
            expandBufferField.setTextPredicate(RecustomTextFieldWidget.INTEGER_FILTER);

            expandButton.setTooltipDelay(1000);
            expandButton.setTooltip(Tooltip.of(Text.of("Expands or collapses this group. This will show or hide its contents on the map.")));
            autoResizeButton.setTooltipDelay(1000);
            autoResizeButton.setTooltip(Tooltip.of(Text.of("Toggle automatic positioning and filter sizing. If enabled, the group will move and resize its filters to fit its contents.")));
            drawModeCollapsedButton.setTooltipDelay(1000);
            drawModeCollapsedButton.setTooltip(Tooltip.of(Text.of("Set how this group appears when collapsed. Point mode shows the group as a normal waypoint. Filter mode draws the group's filter on the map.")));
            drawModeExpandedButton.setTooltipDelay(1000);
            drawModeExpandedButton.setTooltip(Tooltip.of(Text.of("Set how this group appears when expanded. Point mode shows the group as a normal waypoint. Filter mode draws the group's filter on the map.")));
            expandFilterButton.setTooltipDelay(1000);
            expandFilterButton.setTooltip(Tooltip.of(Text.of("Configure the group's expander filter. If this is enabled, the group will be collapsed when the player is too far.")));
            visibleBufferField.setTooltipDelay(1000);
            visibleBufferField.setTooltip(Tooltip.of(Text.of("Configure the visibility filter buffer. When the filter is auto-resized, this is added to the minimum filter radius needed to contain all contents.")));
            expandBufferField.setTooltipDelay(1000);
            expandBufferField.setTooltip(Tooltip.of(Text.of("Configure the expander filter buffer. When the filter is auto-resized, this is added to the minimum filter radius needed to contain all contents.")));
        }
    }

    public EditWaypointScreen(Screen parentScreen, WorldWaypointManager waypointManager, Vector3d pos, UUID groupId, boolean isGroup) {
        this(parentScreen, waypointManager, null, "", "", pos, getRandomColor(), groupId, isGroup);
        this.parentId = groupId;
    }

    public EditWaypointScreen(Screen parentScreen, WorldWaypointManager waypointManager, Waypoint waypoint) {
        this(parentScreen, waypointManager, waypoint.getId(), waypoint.getName(), waypoint.getShortName(), waypoint.getPos(), waypoint.getColorRGB(), waypoint.getParentId(), waypoint instanceof WaypointGroup);

        visibleToggle.setToggled(waypoint.isVisible());
//        temporaryToggle.setToggled(waypoint.isTemporary());

        visibleFilter = waypoint.getVisibleFilter();

        if (isGroup) {
            WaypointGroup group = (WaypointGroup) waypoint;
            expandButton.setToggled(group.isExpanded());
            autoResizeButton.setToggled(group.isAutoResize());
            drawModeCollapsedButton.setCurrentValueIndex(group.getDrawCollapsed().ordinal());
            drawModeExpandedButton.setCurrentValueIndex(group.getDrawExpanded().ordinal());
            visibleBufferField.setText(Integer.toString(group.getVisibleBuffer()));
            expandBufferField.setText(Integer.toString(group.getExpandBuffer()));

            expandFilter = group.getExpandFilter();
        }
    }

    @Override
    protected void init() {
        addDrawableChild(nameField);
        addDrawableChild(shortNameField);
        addDrawableChild(colorSelect);
        addDrawableChild(colorField);
        PrivacyConfig privacyConfig = ShadowMap.getInstance().getConfig().privacyConfig;
        if (privacyConfig.enablePrivateMode.get() && privacyConfig.hideCoords.get()) {
            addDrawable(coordsPrivacyLabel);
        } else {
            addDrawableChild(xField);
            addDrawableChild(yField);
            addDrawableChild(zField);
        }
        addDrawableChild(visibleToggle);
//        addDrawableChild(temporaryToggle);
        addDrawableChild(visibleFilterButton);
        addDrawableChild(groupButton);
        addDrawableChild(deleteButton);
        addDrawableChild(saveButton);
        addDrawableChild(cancelButton);
        addDrawable(titleText);

        if (isGroup) {
            addDrawable(groupLabel);
            addDrawableChild(expandButton);
            addDrawableChild(autoResizeButton);
            addDrawable(drawModeLabel);
            addDrawableChild(drawModeCollapsedButton);
            addDrawableChild(drawModeExpandedButton);
            addDrawable(filterLabel);
            addDrawableChild(expandFilterButton);
            addDrawable(filterBufferLabel);
            addDrawableChild(visibleBufferField);
            addDrawableChild(expandBufferField);
        }

        resize(client, width, height);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        this.width = width;
        this.height = height;
        final int midX = width / 2;
        final int x = midX - 160;
        final int y = (height - 240) / 3;
        int roff = -(titleText.getWidth()) / 2;
        int ry = y + 10;
        titleText.setPosition(midX + roff, ry);

        roff = -(nameField.getWidth() + shortNameField.getWidth() + colorSelect.getWidth() + colorField.getWidth() + 12) / 2;
        ry = y + 24;
        nameField.setPosition(midX + roff, ry);
        roff += nameField.getWidth() + 4;
        shortNameField.setPosition(midX + roff, ry);
        roff += shortNameField.getWidth() + 6;
        colorSelect.setPosition(midX + roff, ry);
        roff += colorSelect.getWidth() + 2;
        colorField.setPosition(midX + roff, ry);

        roff = -(xField.getWidth() + yField.getWidth() + zField.getWidth() + 8) / 2;
        ry += 26;
        xField.setPosition(midX + roff, ry);
        roff += xField.getWidth() + 4;
        yField.setPosition(midX + roff, ry);
        roff += yField.getWidth() + 4;
        zField.setPosition(midX + roff, ry);
        roff = -(coordsPrivacyLabel.getWidth()) / 2;
        coordsPrivacyLabel.setPosition(midX + roff, ry);

        roff = -(visibleToggle.getWidth() + temporaryToggle.getWidth() + 4) / 2;
        ry += 20;
        visibleToggle.setPosition(midX + roff, ry);
        roff += visibleToggle.getWidth() + 4;
        temporaryToggle.setPosition(midX + roff, ry);

        roff = -(groupButton.getWidth()) / 2;
        ry += 22;
        groupButton.setPosition(midX + roff, ry);

        roff = -(visibleFilterButton.getWidth()) / 2;
        ry += 22;
        visibleFilterButton.setPosition(midX + roff, ry);

        if (isGroup) {
            ry += 6;
            groupLabel.setPosition(midX - groupLabel.getWidth() / 2, ry);

            roff = -(expandButton.getWidth() + autoResizeButton.getWidth() + 4) / 2;
            ry += 10;
            expandButton.setPosition(midX + roff, ry);
            roff += expandButton.getWidth() + 4;
            autoResizeButton.setPosition(midX + roff, ry);

            roff = -(drawModeCollapsedButton.getWidth() + visibleFilterButton.getWidth() + visibleBufferField.getWidth() + 16) / 2;
            ry += 34;
            drawModeLabel.setPosition(midX + roff + drawModeCollapsedButton.getWidth() / 2 - drawModeLabel.getWidth() / 2, ry - 10);
            drawModeCollapsedButton.setPosition(midX + roff, ry);
            drawModeExpandedButton.setPosition(midX + roff, ry + 22);
            roff += drawModeCollapsedButton.getWidth() + 12;
            filterLabel.setPosition(midX + roff + visibleFilterButton.getWidth() / 2 - filterLabel.getWidth() / 2, ry - 10);
            visibleFilterButton.setPosition(midX + roff, ry);
            expandFilterButton.setPosition(midX + roff, ry + 22);
            roff += expandFilterButton.getWidth() + 4;
            filterBufferLabel.setPosition(midX + roff + visibleBufferField.getWidth() / 2 - filterBufferLabel.getWidth() / 2, ry - 10);
            visibleBufferField.setPosition(midX + roff, ry + 2);
            expandBufferField.setPosition(midX + roff, ry + 24);
        }

        roff = -(deleteButton.getWidth() + saveButton.getWidth() + cancelButton.getWidth() + 10) / 2;
        ry = y + 210;
        deleteButton.setPosition(midX + roff, ry);
        roff += deleteButton.getWidth() + 8;
        saveButton.setPosition(midX + roff, ry);
        roff += saveButton.getWidth() + 2;
        cancelButton.setPosition(midX + roff, ry);
    }

    @Override
    public void removed() {

    }

    private void deleteClicked(ButtonWidget btn) {
        if (waypointId == null) {
            return;
        }
        Waypoint waypoint = waypointManager.getWaypoint(waypointId).get();
        IconAtlas atlas = ShadowMap.getInstance().getIconAtlas();
        String prompt;
        ConfirmDialogWidget.Option[] options;
        if (waypoint instanceof WaypointGroup group) {
            prompt = "Are you sure you want to delete this group?";
            options = new ConfirmDialogWidget.Option[] {
                    new ConfirmDialogWidget.Option("Keep Contents", atlas.getIcon(Icons.EXPAND_ON), false, () -> {
                        waypointManager.removeWaypoint(waypoint, true);
                        client.setScreen(parentScreen);
                    }),
                    new ConfirmDialogWidget.Option("Delete Contents", atlas.getIcon(Icons.TRASH), false, () -> {
                        waypointManager.removeWaypoint(waypoint, false);
                        client.setScreen(parentScreen);
                    }),
                    new ConfirmDialogWidget.Option("Cancel", atlas.getIcon(Icons.NO_CIRCLE), false, () -> {})
            };
        } else {
            prompt = "Are you sure you want to delete this waypoint?";
            options = new ConfirmDialogWidget.Option[] {
                    new ConfirmDialogWidget.Option("Yes", atlas.getIcon(Icons.TRASH), false, () -> {
                        waypointManager.removeWaypoint(waypoint, true);
                        client.setScreen(parentScreen);
                    }),
                    new ConfirmDialogWidget.Option("Cancel", atlas.getIcon(Icons.NO_CIRCLE), false, () -> {})
            };
        }
        String name = waypoint.getName();
        if (name != null) {
            prompt += "\n\"" + name + '"';
        }
        confirmAction(prompt, options);
    }

    private void saveClicked(ButtonWidget btn) {
        Waypoint waypoint = waypointManager.getWaypoint(waypointId).orElseGet(() -> isGroup ? new WaypointGroup(waypointManager, waypointManager.getUniqueID()) : new Waypoint(waypointManager, new Vector3d(), 0));

        if (!Objects.equals(waypoint.getParentId(), parentId) || waypointManager.getWaypoint(waypointId).isEmpty()) {
            waypointId = waypoint.getId();
            Optional<Waypoint> parent = waypointManager.getWaypoint(parentId);
            if (parentId == null || parent.isPresent() && parent.get() instanceof WaypointGroup) {
                if (!tryAddToGroup(waypoint, parent.orElse(null))) {
                    return;
                }
            } else if (parent.isPresent()) {
                IconAtlas atlas = ShadowMap.getInstance().getIconAtlas();
                confirmAction("Convert waypoint \"" + parent.get().getName() + "\" to a group?",
                        new ConfirmDialogWidget.Option("OK", atlas.getIcon(Icons.EXPAND_ON), false, () -> {
                            if (tryAddToGroup(waypoint, parent.get())) {
                                saveWaypointDetails(waypoint);
                                client.setScreen(new EditWaypointScreen(parentScreen, waypointManager, waypointManager.getWaypoint(
                                        parentId).get()));
                            }
                        }),
                        new ConfirmDialogWidget.Option("Cancel", atlas.getIcon(Icons.NO_CIRCLE), false, () -> {}));
                return;
            } else {
                notifyException("The specified group no longer exists.");
                return;
            }
        }

        saveWaypointDetails(waypoint);

        client.setScreen(parentScreen);
    }

    private void saveWaypointDetails(Waypoint waypoint) {
        waypoint.setName(nameField.getText());
        Vector3d pos = getPosition();
        waypoint.setPos(pos.x, pos.y, pos.z);
        waypoint.setVisible(visibleToggle.isToggled());
        waypoint.setColor(colorSelect.getColor());
//        waypoint.setTemporary(temporaryToggle.isToggled());
        String shortName = shortNameField.getText();
        waypoint.setShortName(shortName == null || shortName.isEmpty() ? null : shortName);

        if (isGroup) {
            WaypointGroup group = (WaypointGroup) waypoint;
            group.setExpanded(expandButton.isToggled());
            group.setDrawCollapsed(drawModeCollapsedButton.getCurrentValue());
            group.setDrawExpanded(drawModeExpandedButton.getCurrentValue());
            group.setAutoResize(autoResizeButton.isToggled(), false);
            try {
                group.setVisibleBuffer(Integer.parseInt(visibleBufferField.getText()), false);
            } catch (NumberFormatException ignore) {}
            try {
                group.setExpandBuffer(Integer.parseInt(expandBufferField.getText()), false);
            } catch (NumberFormatException ignore) {}
            group.setVisibleFilter(visibleFilter, false);
            group.setExpandFilter(expandFilter, false);
            group.recalculatePosition();
        } else {
            waypoint.setVisibleFilter(visibleFilter);
        }
    }

    private boolean tryAddToGroup(Waypoint waypoint, Waypoint group) {
        try {
            waypointManager.addOrMoveWaypoint(waypoint, group == null ? null : group.getId());
            return true;
        } catch (IllegalArgumentException ex) {
            notifyException("Could not add waypoint \"" + waypoint.getName() + "\" to group \"" + group.getName() + '\"');
            return false;
        }
    }

    private void notifyException(String failMessage) {
        confirmAction(failMessage, new ConfirmDialogWidget.Option("OK", null, false, () -> {}));
    }

    private void cancelClicked(ButtonWidget btn) {
        client.setScreen(parentScreen);
    }

    private void colorSelected(ColorSelectWidget selector) {
        colorField.setText(String.format("%06x",  selector.getColor() & 0x00FFFFFF));
    }

    private void colorEntered(String value) {
        if (value.isEmpty()) {
            colorSelect.setColor(0);
            return;
        }
        colorSelect.setColor(Integer.parseInt(value, 16) << (24 - 4 * value.length()));
    }

    private void visibleFilterClicked(ButtonWidget btn) {
        client.setScreen(new EditFilterScreen(this, Text.of("Edit Visibility Filter"), visibleFilter, ShadowMap.getInstance().getConfig().waypointConfig.defaultVisibleDistance.get(), (filter) -> this.visibleFilter = filter));
    }

    private void expandFilterClicked(ButtonWidget btn) {
        client.setScreen(new EditFilterScreen(this, Text.of("Edit Expander Filter"), expandFilter, ShadowMap.getInstance().getConfig().waypointConfig.defaultExpandDistance.get(), (filter) -> this.expandFilter = filter));
    }

    private void groupSelectClicked(ButtonWidget btn) {
        client.setScreen(new WaypointSelectScreen(ShadowMap.getInstance(), this, Text.of("Select Group"), (newGroup) -> {
            if (newGroup == null) {
                parentId = null;
                groupButton.setText("No Group");
            } else {
                parentId = newGroup.getId();
                groupButton.setText("Group: " + newGroup.getName());
            }
        }));
    }

    private Vector3d getPosition() {
        Vector3d vec = new Vector3d();
        try { vec.x = Double.parseDouble(xField.getText()); } catch (NumberFormatException ignore) {}
        try { vec.y = Double.parseDouble(yField.getText()); } catch (NumberFormatException ignore) {}
        try { vec.z = Double.parseDouble(zField.getText()); } catch (NumberFormatException ignore) {}
        return vec;
    }

    private static String getTitle(WorldWaypointManager waypointManager, UUID waypointId, boolean isGroup) {
        String title = (waypointId == null ? "Add " : "Edit ") + (isGroup ? "Group" : "Waypoint");
        Optional<Waypoint> waypoint = waypointManager.getWaypoint(waypointId);
        if (waypoint.isPresent()) {
            title += ": " + waypoint.get().getName();
        }
        return title;
    }

    private static int getRandomColor() {
        float x = (float) Math.random();
        float sat = 1.0F - .75F * x * x;
        x = (float) Math.random();
        float val = 1.0F - .75F * x * x;
        x = (float) Math.random();
        return MapUtils.hsvToRgb(x, sat, val);
    }
}
