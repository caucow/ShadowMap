package com.caucraft.shadowmap.api.util;

import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What's better than a data structure can hold all the data you need? One that
 * gets cleared asynchronously while you're trying to read from it. This
 * provides an intermediary layer to give maps a chance to load/save even if
 * their registries get cleared while doing so.
 */
public class RegistryWrapper<T> {

    private Registry<T> wrapped;
    private Map<Identifier, T> idToValue;
    private Map<T, Identifier> valueToId;
    private boolean canWaitAndCopy;

    public RegistryWrapper(Registry<T> wrapped) {
        this.wrapped = wrapped;
        this.idToValue = new ConcurrentHashMap<>();
        this.valueToId = Collections.synchronizedMap(new IdentityHashMap<>());
        copyValues();
    }

    private void copyValues() {
        for (var entry : wrapped.getEntrySet()) {
            idToValue.put(entry.getKey().getValue(), entry.getValue());
            valueToId.put(entry.getValue(), entry.getKey().getValue());
        }
    }

    private void tryWaitAndCopy() {
        if (!canWaitAndCopy) {
            return;
        }
        synchronized(this) {
            if (!canWaitAndCopy) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            copyValues();
        }
    }

    public void setWrapped(Registry<T> wrapped) {
        this.wrapped = wrapped;
        copyValues();
        canWaitAndCopy = true;
    }

    public T getValue(Identifier id) {
        T value = wrapped.get(id);
        if (value != null) {
            return value;
        }
        value = idToValue.get(id);
        if (value != null) {
            return value;
        }
        tryWaitAndCopy();
        return idToValue.get(id);
    }

    public Optional<T> getValueOrEmpty(Identifier id) {
        T value = wrapped.get(id);
        if (value != null) {
            return Optional.of(value);
        }
        value = idToValue.get(id);
        if (value != null) {
            return Optional.of(value);
        }
        return Optional.empty();
    }

    public Identifier getId(T value) {
        Identifier id = wrapped.getId(value);
        if (id != null) {
            return id;
        }
        id = valueToId.get(value);
        if (id != null) {
            return id;
        }
        tryWaitAndCopy();
        return valueToId.get(value);
    }
}
