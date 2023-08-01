package com.caucraft.shadowmap.client.util.data;

import com.caucraft.shadowmap.client.ShadowMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtLong;

import java.util.UUID;
import java.util.function.BiFunction;

public class DeletableLiveDataMap<V extends DeletableLiveObject> extends Object2ObjectLinkedOpenHashMap<UUID, V> {
    private BiFunction<UUID, NbtCompound, V> valueSupplier;
    private Object2LongOpenHashMap<UUID> deleteTimes;
    private BiFunction<V, V, V> mergeFunction;

    public DeletableLiveDataMap(BiFunction<UUID, NbtCompound, V> valueSupplier) {
        this.valueSupplier = valueSupplier;
        this.deleteTimes = new Object2LongOpenHashMap<>();
    }

    public DeletableLiveDataMap<V> setMergeFunction(BiFunction<V, V, V> mergeFunction) {
        this.mergeFunction = mergeFunction;
        return this;
    }

    public void loadNbt(NbtCompound root) {
        NbtCompound liveRoot = root.getCompound("live");
        NbtCompound deletes = root.getCompound("deleted");

        for (String key : deletes.getKeys()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                id = UUID.randomUUID();
                ShadowMap.getLogger().warn("Invalid deleted UUID will be ignored: " + key, ex);
            }
            long value = deletes.getLong(key);
            if (deleteTimes.containsKey(id)) {
                value = Math.max(value, deleteTimes.getLong(id));
            }
            deleteTimes.put(id, value);
        }

        for (String key : liveRoot.getKeys()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                id = UUID.randomUUID();
                ShadowMap.getLogger().warn("Invalid UUID, " + key + " will be replaced with " + id, ex);
            }
            if (deleteTimes.containsKey(id)) {
                continue;
            }
            NbtCompound liveCompound = liveRoot.getCompound(key);
//            if (liveCompound.getBoolean(DeletableLiveObject.TEMP_KEY) && !containsKey(id)) {
//                continue;
//            }
            V newValue = valueSupplier.apply(id, liveCompound);
            newValue.loadNbt(liveCompound);
            V oldValue = computeIfAbsent(id, (k) -> newValue);
            if (oldValue == newValue) {
                continue;
            }
            if (mergeFunction != null) {
                put(id, mergeFunction.apply(oldValue, newValue));
            } else if (newValue.getModified() > oldValue.getModified()) {
                put(id, newValue);
            }
        }

        for (UUID key : deleteTimes.keySet()) {
            remove(key);
        }
    }

    public NbtCompound toNbt() {
        long time = ShadowMap.getLastTickTimeS();
        NbtCompound root = new NbtCompound();
        NbtCompound live = new NbtCompound();
        for (var entry : entrySet()) {
            live.put(entry.getKey().toString(), entry.getValue().toNbt());
        }
        NbtCompound deleted = new NbtCompound();
        for (var entry : deleteTimes.object2LongEntrySet()) {
            if (entry.getLongValue() + 86_400_000 > time) {
                deleted.put(entry.getKey().toString(), NbtLong.of(entry.getLongValue()));
            }
        }
        root.put("live", live);
        root.put("deleted", deleted);
        return root;
    }

    @Override
    public V remove(Object key) {
        V removed = super.remove(key);
        if (removed != null && key instanceof UUID id && (id.getMostSignificantBits() & 0xFFFF_FFFF_0000_0000L) != 0) {
            deleteTimes.put(id, ShadowMap.getLastTickTimeS());
        }
        return removed;
    }

    @Override
    public boolean remove(Object key, Object value) {
        boolean removed = super.remove(key, value);
        if (removed && key instanceof UUID id) {
            deleteTimes.put(id, ShadowMap.getLastTickTimeS());
        }
        return removed;
    }
}
