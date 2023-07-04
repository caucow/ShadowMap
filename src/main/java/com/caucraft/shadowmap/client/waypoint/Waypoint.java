package com.caucraft.shadowmap.client.waypoint;

import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.util.data.DeletableLiveObject;
import it.unimi.dsi.fastutil.chars.CharPredicate;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector2f;
import org.joml.Vector3d;

import java.util.Objects;
import java.util.UUID;

public class Waypoint extends DeletableLiveObject {
    public static String TYPE_KEY = "type";

    final transient WorldWaypointManager waypointManager;
    protected String name;
    protected String shortName;
    protected final Vector3d pos;
    protected final Vector2f dir;
    protected int color;
    protected boolean visible;
    protected WaypointFilter visibleFilter;
    protected UUID parentId;
    protected transient WaypointGroup parent;
    String cachedLabel;

    Waypoint(Waypoint inheritFrom) {
        super(inheritFrom);
        this.waypointManager = inheritFrom.waypointManager;
        this.name = inheritFrom.name;
        this.shortName = inheritFrom.shortName;
        this.pos = inheritFrom.pos;
        this.dir = inheritFrom.dir;
        this.color = inheritFrom.color;
        this.visible = inheritFrom.visible;
        this.visibleFilter = inheritFrom.visibleFilter;
    }

    Waypoint(WorldWaypointManager waypointManager) {
        this(waypointManager, waypointManager.getUniqueID());
    }

    public Waypoint(WorldWaypointManager waypointManager, UUID id) {
        super(id);
        this.waypointManager = waypointManager;
        this.name = "";
        this.shortName = null;
        this.pos = new Vector3d();
        this.dir = new Vector2f();
        this.visible = true;
    }

    public Waypoint(WorldWaypointManager waypointManager, Vector3d pos, int colorRGB) {
        super(waypointManager.getUniqueID());
        this.waypointManager = waypointManager;
        this.name = "";
        this.pos = Objects.requireNonNull(pos, "Waypoint must have a (non-null) position");
        this.dir = new Vector2f();
        this.color = colorRGB;
        this.visible = true;
    }

    protected void setModified(long modified) {
        super.setModified(modified);
        waypointManager.setModified(modified);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (!this.name.equals(name)) {
            setModified(ShadowMap.getLastTickTimeS());
        }
        this.name = Objects.requireNonNull(name, "Waypoint name cannot be null");
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        if (!Objects.equals(this.shortName, shortName)) {
            setModified(ShadowMap.getLastTickTimeS());
        }
        this.shortName = shortName;
    }

    public String getShortOrLongName() {
        String shortName = this.shortName;
        if (shortName != null && !shortName.isEmpty()) {
            return shortName;
        }
        return this.name;
    }

    /**
     * @return the waypoint's (mutable) direction vector. {@link Vector2f#x} is
     * pitch, {@link Vector2f#y} is yaw.
     */
    public Vector2f getDir() {
        return dir;
    }

    /**
     * Sets the direction vectors x (pitch) and y (yaw) components to those of
     * the provided vector.
     * @param dir component source vector
     */
    public void setDir(Vector2f dir) {
        if (!this.dir.equals(dir)) {
            setModified(ShadowMap.getLastTickTimeS());
        }
        this.dir.set(dir);
    }

    /**
     * Sets the direction vector's x (pitch) and y (yaw) components to the
     * provided values.
     * @param pitch pitch component
     * @param yaw yaw component
     */
    public void setDir(float pitch, float yaw) {
        this.dir.set(pitch, yaw);
    }

    /**
     * @return the waypoint's (mutable) position vector.
     */
    public Vector3d getPos() {
        return pos;
    }

    /**
     * Sets the position vector's x, y, and z components to those of the
     * provided vector
     * @param pos component source vector
     */
    public void setPos(Vector3d pos) {
        if (!this.pos.equals(pos)) {
            setModified(ShadowMap.getLastTickTimeS());
        }
        this.pos.set(pos);
    }

    /**
     * Sets the position vector's x, y, and z components to the provided values.
     * @param x x axis component
     * @param y y axis component
     * @param z z axis component
     */
    public void setPos(double x, double y, double z) {
        if (pos.x != x || pos.y != y || pos.z != z) {
            setModified(ShadowMap.getLastTickTimeS());
        }
        this.pos.set(x, y, z);
    }

    /**
     * @return the color of the waypoint, which should be encoded in the format
     * 0x__RRGGBB
     */
    public int getColorRGB() {
        return color;
    }

    /**
     * @param color the new color of the waypoint, which should be encoded in
     * the format 0x__RRGGBB
     */
    public void setColor(int color) {
        if (this.color != color) {
            setModified(ShadowMap.getLastTickTimeS());
        }
        this.color = color;
    }

    /**
     * Sets the color integer using the provided component colors. Provided
     * values are clamped to the range 0-255.
     * @param red red component
     * @param green green component
     * @param blue blue component
     */
    public void setColor(int red, int green, int blue) {
        red = MathHelper.clamp(red, 0, 255);
        green = MathHelper.clamp(green, 0, 255);
        blue = MathHelper.clamp(blue, 0, 255);
        setColor(red << 16 | green << 8 | blue);
    }

    /**
     * @param x x coordinate to test against this waypoint and its filters
     * @param z z coordinate to test against this waypoint and its filters
     * @return true if this waypoint is visible and either does not have a
     * filter, its filter is disabled, or its filter contains the coordinates,
     * false otherwise.
     */
    public boolean isVisibleAt(double x, double z, boolean ignoreFilter) {
        if (!isVisible()) {
            return false;
        }
        WaypointFilter filter = getVisibleFilter();
        if (filter == null || !filter.isEnabled() || ignoreFilter) {
            return true;
        }
        return filter.contains(MathHelper.floor(x - pos.x), MathHelper.floor(z - pos.z));
    }

    /**
     * @return the visibility flag for this waypoint. If this is false, the
     * visibility filter returned by {@link #getVisibleFilter()} should be
     * ignored.
     */
    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        if (this.visible != visible) {
            setModified(ShadowMap.getLastTickTimeS());
        }
        this.visible = visible;
    }

    public WaypointFilter getVisibleFilter() {
        return visibleFilter;
    }

    public void setVisibleFilter(WaypointFilter visibleFilter) {
        if (!Objects.equals(this.visibleFilter, visibleFilter)) {
            setModified(ShadowMap.getLastTickTimeS());
        }
        this.visibleFilter = visibleFilter;
    }

    public WaypointGroup getParent() {
        return parent;
    }

    public UUID getParentId() {
        return parentId;
    }

    void setParent(WaypointGroup parent) {
        if (this.parent != parent) {
            setModified(ShadowMap.getLastTickTimeS());
        }
        this.parent = parent;
        this.parentId = parent == null ? null : parent.getId();
    }

    public WaypointType getType() {
        return WaypointType.POINT;
    }

    public void loadNbt(NbtCompound root) {
        super.loadNbt(root);
        parent = null;

        this.name = root.getString("name");
        if (root.contains("shortName", NbtElement.STRING_TYPE)) {
            this.shortName = root.getString("shortName");
        } else {
            this.shortName = null;
        }
        if (root.contains("pos", NbtElement.COMPOUND_TYPE)) {
            NbtCompound compound = root.getCompound("pos");
            this.pos.set(compound.getDouble("x"), compound.getDouble("y"), compound.getDouble("z"));
        } else {
            this.pos.set(0, 0, 0);
        }
        if (root.contains("dir", NbtElement.COMPOUND_TYPE)) {
            NbtCompound compound = root.getCompound("dir");
            this.dir.set(compound.getFloat("p"), compound.getFloat("y"));
        }
        this.color = root.getInt("color");
        this.visible = root.getBoolean("visible");
        if (root.contains("visibleFilter", NbtElement.COMPOUND_TYPE)) {
            this.visibleFilter = new WaypointFilter();
            this.visibleFilter.loadNbt(root.getCompound("visibleFilter"));
        }
        if (root.contains("parent", NbtElement.STRING_TYPE)) {
            try {
                this.parentId = UUID.fromString(root.getString("parent"));
            } catch (IllegalArgumentException ex) {
                ShadowMap.getLogger().warn("Invalid UUID for parent waypoint: " + root.getString("parent") + " -> " + getId(), ex);
            }
        }
    }

    public NbtCompound toNbt() {
        NbtCompound root = super.toNbt();
        root.put("name", NbtString.of(getName()));
        String shortName = getShortName();
        if (shortName != null) {
            root.put("shortName", NbtString.of(shortName));
        }
        Vector3d lpos = getPos();
        if (!lpos.equals(0, 0, 0)) {
            NbtCompound compound = new NbtCompound();
            compound.put("x", NbtDouble.of(lpos.x));
            compound.put("y", NbtDouble.of(lpos.y));
            compound.put("z", NbtDouble.of(lpos.z));
            root.put("pos", compound);
        }
        Vector2f ldir = getDir();
        if (!ldir.equals(0, 0)) {
            NbtCompound compound = new NbtCompound();
            compound.put("p", NbtFloat.of(ldir.x));
            compound.put("y", NbtFloat.of(ldir.y));
        }
        root.put("color", NbtInt.of(getColorRGB()));
        root.put("visible", NbtByte.of(isVisible()));
        WaypointFilter lfilter = getVisibleFilter();
        if (lfilter != null) {
            root.put("visibleFilter", lfilter.toNbt());
        }
        WaypointGroup lparent = getParent();
        if (lparent != null) {
            root.put("parent", NbtString.of(lparent.getId().toString()));
        }
        root.put("type", NbtString.of(getType().name()));
        return root;
    }

    @Override
    public String toString() {
        if (name.isEmpty()) {
            return getId().toString();
        }
        return name + "-" + getId().toString();
    }

    /**
     * Gets a short name or abbreviation from a long name, prioritizing a
     * numeric suffix if one exists and otherwise picking up every first letter
     * or digit in a word or number.
     * @param longName the long name to abbreviate.
     * @return the shortened abbreviation, or null if an abbreviation could not
     * be made.
     */
    public static String getShortName(String longName) {
        if (longName == null || longName.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        char[] chars = longName.toCharArray();
        int numStart = -1, numEnd = -1;
        for (int i = chars.length - 1; i >= 0; i--) {
            char c = chars[i];
            boolean isDigit = Character.isDigit(c);
            if (isDigit) {
                if (numEnd == -1) {
                    numEnd = i + 1;
                }
                numStart = i;
            } else if (numEnd != -1 || !Character.isWhitespace(c)) {
                break;
            }
        }
        int length = numEnd == -1 ? chars.length : numStart;
        int limit = numEnd == -1 ? 4 : 4 - Math.min(3, numEnd - numStart);
        for (int i = 0; i < length && limit > 0; i++) {
            char c = chars[i];
            if (!Character.isLetterOrDigit(c)) {
                // Skip non-alphanumeric
                continue;
            }
            sb.append(c);
            limit--;
            CharPredicate test = Character.isDigit(c) ? Character::isDigit : Character::isLowerCase;
            i++;
            for (;i < length && test.test(chars[i]); i++) {}
            i--;
        }
        if (numEnd != -1) {
            sb.append(chars, numStart, Math.min(3, numEnd - numStart));
        }
        return sb.toString();
    }

    public enum WaypointType {
        POINT(),
        GROUP();
    }
}
