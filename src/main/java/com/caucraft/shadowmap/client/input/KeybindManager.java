package com.caucraft.shadowmap.client.input;

import com.google.common.annotations.Beta;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import net.minecraft.client.util.InputUtil;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * This keybind system is part of a test for a larger planned user interface API
 * and is likely to be relocated (to a different project) in the hopefully-near
 * future. Such a change will likely come with other breaking API changes in
 * ShadowMap.
 */
@Beta
public class KeybindManager {

    private ListMultimap<InputUtil.Key, Keybind> triggerMap;
    private LinkedList<Keybind> pressedKeybinds;
    private Set<InputUtil.Key> pressedKeys;

    public KeybindManager() {
        this.triggerMap = MultimapBuilder.hashKeys().arrayListValues().build();
        this.pressedKeybinds = new LinkedList<Keybind>();
        this.pressedKeys = new HashSet<>();
    }

    public void press(int keyCode, int scanCode) {
        InputUtil.Key pressedKey = InputUtil.fromKeyCode(keyCode, scanCode);
        if (!pressedKeys.add(pressedKey)) {
            return;
        }
        // Update pressed state of typed keybinds
        Iterator<Keybind> pressedIterator = pressedKeybinds.iterator();
        while (pressedIterator.hasNext()) {
            Keybind next = pressedIterator.next();
            if (!next.isExclusive() && !next.matchesPressedKeys(pressedKeys)) {
                next.setPressed(false, true);
                pressedIterator.remove();
            }
        }
        // Trigger new keybinds
        List<Keybind> triggeredKeybinds = triggerMap.get(pressedKey);
        for (Keybind keybind : triggeredKeybinds) {
            if (!keybind.isPressed() && keybind.matchesPressedKeys(pressedKeys)) {
                keybind.setPressed(true, true);
                pressedKeybinds.add(keybind);
            }
        }
    }

    public void release(int keyCode, int scanCode) {
        InputUtil.Key releasedKey = InputUtil.fromKeyCode(keyCode, scanCode);
        if (!pressedKeys.remove(releasedKey)) {
            return;
        }
        Iterator<Keybind> pressedIterator = pressedKeybinds.iterator();
        while (pressedIterator.hasNext()) {
            Keybind next = pressedIterator.next();
            if (!next.matchesPressedKeys(pressedKeys)) {
                next.setPressed(false, true);
                next.addTyped(true);
                pressedIterator.remove();
            }
        }
    }

    public void reset() {
        for (Keybind keybind : pressedKeybinds) {
            keybind.setPressed(false, true);
        }
        pressedKeybinds.clear();
        pressedKeys.clear();
    }
}
