package com.caucraft.shadowmap.api.util;

@FunctionalInterface
public interface ObjectToIntFunction<V> {
    int apply(V value);
}
