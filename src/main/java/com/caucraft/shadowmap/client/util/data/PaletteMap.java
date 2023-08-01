package com.caucraft.shadowmap.client.util.data;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A low-effort solution to saving and loading a palette. Registering a new
 * value with the palette will generate a new ID for it, and registering an
 * existing value will return the existing ID. IDs are generated starting from
 * 1, leaving 0 to be used as a null/invalid ID by the user.
 * @param <V> the type of the paletted value.
 */
public class PaletteMap<V> {

    private final Reference2IntMap<V> ids;
    private final List<V> valueList;
    private final NbtList nbtList;

    public PaletteMap() {
        this.ids = new Reference2IntOpenHashMap<>(256);
        this.valueList = new ArrayList<>();
        this.nbtList = new NbtList();
    }

    /**
     * Generates a new ID if the provided {@code valueString} is new, otherwise
     * returns the existing ID mapped to by valueString. IDs are generated
     * starting at 1, leaving 0 to be used as a null/invalid ID by the user.
     * @param valueString the String representation of the value
     * @param value the value itself
     * @return an ID corresponding to the value in the palette.
     */
    public int registerId(Supplier<String> valueString, V value) {
        if (!ids.containsKey(value)) {
            int id = ids.size() + 1;
            ids.put(value, id);
            valueList.add(value);
            nbtList.add(NbtString.of(valueString.get()));
            return id;
        }
        return ids.getInt(value);
    }

    /**
     * Gets the value corresponding to the provided ID.
     * @param paletteId the ID of the value in the palette. This is effectively
     * its index in the internal list offset by one, but the user need not
     * account for this offset.
     * @return The value corresponding with the provided ID, or {@code null} if
     * the ID is not present in the palette (id <= 0 or > paletteSize)
     */
    public V getValue(int paletteId) {
        if (paletteId <= 0 || paletteId > valueList.size()) {
            return null;
        }
        return valueList.get(paletteId - 1);
    }

    /**
     * @return the NbtList (String list) backing the palette to be written to
     * file.
     */
    public NbtList getNbt() {
        return nbtList;
    }

    /**
     * Loads the palette from the provided (String) NBT list, using the provided
     * function to convert the string representations of palette values to the
     * values themselves.
     * @param list the (String) NBT list backing this palette.
     * @param valueDeserializer a function to convert the Strings to values.
     */
    public void loadNbt(NbtList list, Function<String, V> valueDeserializer) {
        this.ids.clear();
        this.valueList.clear();
        this.nbtList.clear();
        for (int i = 0; i < list.size(); i++) {
            V value = valueDeserializer.apply(list.getString(i));
            this.ids.put(value, i);
            this.valueList.add(value);
            this.nbtList.add(list.get(i));
        }
    }

    public int size() {
        return ids.size();
    }
}
