package com.caucraft.shadowmap.client.config.values;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public abstract class ConfigValue {
    private final ConfigSection section;
    private boolean isCustom;

    protected ConfigValue(ConfigSection section) {
        this.section = section;
    }

    public boolean isCustom() {
        return isCustom;
    }

    protected void setUsesCustom(boolean usesCustom) {
        this.isCustom = usesCustom;
    }

    protected void setDirty(boolean dirty) {
        if (dirty && section != null) {
            section.setDirty(dirty);
        }
    }

    public abstract void restoreDefault();
    public abstract JsonElement toJson();
    public abstract void loadJson(JsonObject root, String key);
}
