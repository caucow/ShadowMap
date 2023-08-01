package com.caucraft.shadowmap.client.config.values;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class ByteValue extends ConfigValue {
    private final byte defaultValue;
    private byte value;

    public ByteValue(ConfigSection section, byte defaultValue) {
        super(section);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public byte get() {
        return value;
    }

    public void set(byte value) {
        byte oldValue = this.value;
        this.value = value;
        if (oldValue != value) {
            setDirty(true);
            setUsesCustom(true);
        }
    }

    @Override
    public void restoreDefault() {
        this.value = defaultValue;
        if (isCustom()) {
            setUsesCustom(false);
            setDirty(true);
        }
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void loadJson(JsonObject root, String key) {
        if (root.has(key)) {
            this.value = root.get(key).getAsByte();
            setUsesCustom(true);
        }
    }
}
