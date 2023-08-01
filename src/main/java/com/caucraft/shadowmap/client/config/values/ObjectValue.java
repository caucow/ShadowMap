package com.caucraft.shadowmap.client.config.values;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ObjectValue<T> extends ConfigValue {
    private final T defaultValue;
    private final Function<T, JsonElement> saveFunction;
    private final BiFunction<JsonElement, T, T> loadFunction;
    private T value;

    public ObjectValue(ConfigSection section, T defaultValue, Function<T, JsonElement> saveFunction, BiFunction<JsonElement, T, T> loadFunction) {
        super(section);
        this.defaultValue = defaultValue;
        this.saveFunction = saveFunction;
        this.loadFunction = loadFunction;
        this.value = defaultValue;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        T oldValue = this.value;
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
        return saveFunction.apply(value);
    }

    @Override
    public void loadJson(JsonObject root, String key) {
        if (root.has(key)) {
            this.value = loadFunction.apply(root, value);
            setUsesCustom(true);
        }
    }
}
