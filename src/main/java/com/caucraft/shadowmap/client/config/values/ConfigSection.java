package com.caucraft.shadowmap.client.config.values;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;

public class ConfigSection extends ConfigValue {
    private JsonObject json;
    private Map<String, ConfigValue> values;
    private boolean dirty;

    public ConfigSection(ConfigSection section) {
        super(section);
        this.values = new Object2ObjectOpenHashMap<>();
        super.setUsesCustom(true);
    }

    public ConfigSection(ConfigSection section, JsonObject root) {
        super(section);
        this.json = root;
    }

    @Override
    protected void setUsesCustom(boolean usesCustom) {}

    @Override
    public void setDirty(boolean dirty) {
        if (this.dirty != dirty) {
            this.dirty = dirty;
            super.setDirty(dirty);
        }
    }

    public ConfigSection getSection(String name) {
        ConfigValue value = values.get(name);
        if (value != null) {
            return (ConfigSection) value;
        }
        JsonObject root = json;
        ConfigSection newValue;
        if (root == null) {
            newValue = new ConfigSection(this);
        } else {
            newValue = new ConfigSection(this, root);
            root.remove(name);
        }
        values.put(name, newValue);
        return newValue;
    }

    public BooleanValue getBoolean(String name, boolean defaultValue) {
        ConfigValue value = values.get(name);
        if (value != null) {
            return (BooleanValue) value;
        }
        BooleanValue newValue = new BooleanValue(this, defaultValue);
        values.put(name, newValue);
        JsonObject root = json;
        if (root != null) {
            newValue.loadJson(root, name);
            root.remove(name);
        }
        return newValue;
    }

    public ByteValue getByte(String name, byte defaultValue) {
        ConfigValue value = values.get(name);
        if (value != null) {
            return (ByteValue) value;
        }
        ByteValue newValue = new ByteValue(this, defaultValue);
        values.put(name, newValue);
        JsonObject root = json;
        if (root != null) {
            newValue.loadJson(root, name);
            root.remove(name);
        }
        return newValue;
    }

    public ShortValue getShort(String name, short defaultValue) {
        ConfigValue value = values.get(name);
        if (value != null) {
            return (ShortValue) value;
        }
        ShortValue newValue = new ShortValue(this, defaultValue);
        values.put(name, newValue);
        JsonObject root = json;
        if (root != null) {
            newValue.loadJson(root, name);
            root.remove(name);
        }
        return newValue;
    }

    public IntValue getInt(String name, int defaultValue) {
        ConfigValue value = values.get(name);
        if (value != null) {
            return (IntValue) value;
        }
        IntValue newValue = new IntValue(this, defaultValue);
        values.put(name, newValue);
        JsonObject root = json;
        if (root != null) {
            newValue.loadJson(root, name);
            root.remove(name);
        }
        return newValue;
    }

    public LongValue getLong(String name, long defaultValue) {
        ConfigValue value = values.get(name);
        if (value != null) {
            return (LongValue) value;
        }
        LongValue newValue = new LongValue(this, defaultValue);
        values.put(name, newValue);
        JsonObject root = json;
        if (root != null) {
            newValue.loadJson(root, name);
            root.remove(name);
        }
        return newValue;
    }

    public FloatValue getFloat(String name, float defaultValue) {
        ConfigValue value = values.get(name);
        if (value != null) {
            return (FloatValue) value;
        }
        FloatValue newValue = new FloatValue(this, defaultValue);
        values.put(name, newValue);
        JsonObject root = json;
        if (root != null) {
            newValue.loadJson(root, name);
            root.remove(name);
        }
        return newValue;
    }

    public DoubleValue getDouble(String name, double defaultValue) {
        ConfigValue value = values.get(name);
        if (value != null) {
            return (DoubleValue) value;
        }
        DoubleValue newValue = new DoubleValue(this, defaultValue);
        values.put(name, newValue);
        JsonObject root = json;
        if (root != null) {
            newValue.loadJson(root, name);
            root.remove(name);
        }
        return newValue;
    }

    public StringValue getString(String name, String defaultValue) {
        ConfigValue value = values.get(name);
        if (value != null) {
            return (StringValue) value;
        }
        StringValue newValue = new StringValue(this, defaultValue);
        values.put(name, newValue);
        JsonObject root = json;
        if (root != null) {
            newValue.loadJson(root, name);
            root.remove(name);
        }
        return newValue;
    }

    public <T extends Enum<T>> EnumValue<T> getEnum(String name, T defaultValue, Class<T> enumClass) {
        ConfigValue value = values.get(name);
        if (value != null) {
            return (EnumValue<T>) value;
        }
        EnumValue<T> newValue = new EnumValue<>(this, enumClass, defaultValue);
        values.put(name, newValue);
        JsonObject root = json;
        if (root != null) {
            newValue.loadJson(root, name);
            root.remove(name);
        }
        return newValue;
    }

    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void restoreDefault() {
        for (ConfigValue value : values.values()) {
            value.restoreDefault();
        }
    }

    @Override
    public JsonElement toJson() {
        JsonObject root = json;
        if (root == null) {
            root = new JsonObject();
        } else {
            root = root.deepCopy();
        }
        for (Map.Entry<String, ConfigValue> entry : values.entrySet()) {
            if (entry.getValue().isCustom()) {
                root.add(entry.getKey(), entry.getValue().toJson());
            }
        }
        this.dirty = false;
        return root;
    }

    @Override
    public void loadJson(JsonObject root, String key) {
        JsonObject json = this.json = key == null ? root : root.getAsJsonObject(key);
        for (Map.Entry<String, ConfigValue> entry : values.entrySet()) {
            String innerKey = entry.getKey();
            ConfigValue value = entry.getValue();
            if (json.has(entry.getKey())) {
                value.loadJson(json, innerKey);
                json.remove(innerKey);
            }
        }
    }
}
