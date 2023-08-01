package com.caucraft.shadowmap.client.config.values;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Objects;

public class StringValue extends ConfigValue {
    private final String defaultValue;
    private String value;

    public StringValue(ConfigSection section, String defaultValue) {
        super(section);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String get() {
        return value;
    }

    public void set(String value) {
        String oldValue = this.value;
        this.value = value;
        if (!Objects.equals(oldValue, value)) {
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
            this.value = root.get(key).getAsString();
            setUsesCustom(true);
        }
    }
}
