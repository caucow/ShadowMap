package com.caucraft.shadowmap.client.util.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtLong;

import java.util.UUID;

public abstract class DeletableLiveObject {
    public static final String CREATED_KEY = "created";
    public static final String MODIFIED_KEY = "modified";
//    public static final String TEMP_KEY = "~temp";

    private transient final UUID id;
    private long created;
    private long modified;
//    private boolean temporary;

    protected DeletableLiveObject(UUID id) {
        this.id = id;
        this.created = System.currentTimeMillis();
        this.modified = created;
    }

    protected DeletableLiveObject(DeletableLiveObject inheritFrom) {
        this.id = inheritFrom.id;
        this.created = inheritFrom.created;
        this.modified = inheritFrom.modified;
    }

    public UUID getId() {
        return id;
    }

    protected void setCreated(long created) {
        this.created = created;
    }

    /**
     * @return the system time when the waypoint was created (in milliseconds).
     */
    public long getCreated() {
        return created;
    }

    protected void setModified(long modified) {
        this.modified = modified;
    }

    public long getModified() {
        return modified;
    }

//    public boolean isTemporary() {
//        return temporary;
//    }
//
//    public void setTemporary(boolean temporary) {
//        if (this.temporary != temporary) {
//            setModified(ShadowMap.getLastTickTimeS());
//        }
//        this.temporary = temporary;
//    }

    public void loadNbt(NbtCompound root) {
        this.created = root.getLong(CREATED_KEY);
        this.modified = root.getLong(MODIFIED_KEY);
    }

    public NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();
        root.put(CREATED_KEY, NbtLong.of(created));
        root.put(MODIFIED_KEY, NbtLong.of(modified));
        return root;
    }
}
