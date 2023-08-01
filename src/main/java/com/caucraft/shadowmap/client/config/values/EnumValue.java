package com.caucraft.shadowmap.client.config.values;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class EnumValue<T extends Enum<T>> extends ConfigValue {
    private final Class<T> enumClass;
    private final T defaultValue;
    private T value;

    public EnumValue(ConfigSection section, Class<T> enumClass, T defaultValue) {
        super(section);
        this.enumClass = enumClass;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        T oldValue = this.value;
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
        return new JsonPrimitive(value.name());
    }

    @Override
    public void loadJson(JsonObject root, String key) {
        if (root.has(key)) {
            try {
                this.value = Enum.valueOf(enumClass, root.get(key).getAsString());
                setUsesCustom(true);
            } catch (IllegalArgumentException ex) {
                this.value = defaultValue;
            }
        }
    }
}
