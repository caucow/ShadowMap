package com.caucraft.shadowmap.client.config.values;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class FloatValue extends ConfigValue {
    private final float defaultValue;
    private float value;

    public FloatValue(ConfigSection section, float defaultValue) {
        super(section);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public float get() {
        return value;
    }

    public void set(float value) {
        float oldValue = this.value;
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
            this.value = root.get(key).getAsFloat();
            setUsesCustom(true);
        }
    }
}
