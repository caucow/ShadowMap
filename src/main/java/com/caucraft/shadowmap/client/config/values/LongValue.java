package com.caucraft.shadowmap.client.config.values;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class LongValue extends ConfigValue {
    private final long defaultValue;
    private long value;

    public LongValue(ConfigSection section, long defaultValue) {
        super(section);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public long get() {
        return value;
    }

    public void set(long value) {
        long oldValue = this.value;
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
            this.value = root.get(key).getAsLong();
            setUsesCustom(true);
        }
    }
}
