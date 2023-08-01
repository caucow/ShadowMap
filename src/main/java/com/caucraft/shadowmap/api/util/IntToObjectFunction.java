package com.caucraft.shadowmap.api.util;

@FunctionalInterface
public interface IntToObjectFunction<R> {
    R apply(int value);
}
