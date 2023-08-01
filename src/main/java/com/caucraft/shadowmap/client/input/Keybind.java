package com.caucraft.shadowmap.client.input;

import com.google.common.annotations.Beta;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

/**
 * A more flexible implementation of keybindings than Minecraft's vanilla
 * {@link KeyBinding} class. This allows for not only Ctrl/Shift/Alt key
 * modifiers, but arbitrary key modifier combinations including character and
 * functional keys. Additionally, keybinds can be {@link #setExclusive()}
 * (causing them to only activate when their trigger and modifiers are the only
 * keys pressed) or modifier-only.<br>
 * <br>
 * Keybinds can either be repeatedly polled (on tick/frame) using
 * {@link #isPressed()} or {@link #wasTyped()}, or callbacks can be provided
 * using {@link #onPressed(Runnable)}, {@link #onReleased(Runnable)}, and
 * {@link #onTyped(Runnable)}.<br>
 * <br>
 * The following table describes the behaviors of each event method in inclusive
 * vs. exclusive mode:<br>
 * <table>
 *     <tr>
 *         <th></th>
 *         <th>onPressed</td>
 *         <th>onReleased</td>
 *         <th>onTyped</td>
 *     </tr>
 *     <tr>
 *         <th>Inclusive/Held Mode</th>
 *         <td>Called when the trigger key is pressed while modifiers are held,
 *         regardless of other keys also pressed.</td>
 *         <td>Called when any of the required keys is released, regardless of
 *         other keys also pressed.</td>
 *         <td>Called when any of the required keys is released, regardless of
 *         other keys also pressed.</td>
 *     </tr>
 *     <tr>
 *         <th>Exclusive/Typed Mode</th>
 *         <td>Called when the trigger is pressed and the trigger and modifier
 *         keys are the only keys held.</td>
 *         <td>Called when the trigger or a modifier key is released or any
 *         unused key is pressed.</td>
 *         <td>Called when released only due to the trigger or a modifier being
 *         released.</td>
 *     </tr>
 * </table><br>
 * A keybind can have a null trigger key, in which case it will be considered a
 * modifier-only keybind. Modifier-only keybinds are processed before triggered
 * keybinds and are not checked for conflicts in any context.<br>
 * <br>
 * Triggered keybinds with the same trigger key are tested for conflicts and
 * considered conflicting if both one keybind's modifier list contains another
 * keybind's modifier list, and the keybind with fewer (or equal number of)
 * modifiers is not exclusive. Unlike vanilla keybinds however, conflicting
 * keybinds will both be triggered when pressed at the same time, and it is up to
 * the user to decide whether this is problematic.<br>
 * <br>
 * This class is XKCD 927 compliant.<br>
 * <br>
 * This keybind system is part of a test for a larger planned user interface API
 * and is likely to be relocated (to a different project) in the hopefully-near
 * future. Such a change will likely come with other breaking API changes in
 * ShadowMap.
 */
@Beta
public class Keybind {

    private static InputUtil.Key[] codesToKeys(int[] modifierCodes) {
        InputUtil.Key[] keys = new InputUtil.Key[modifierCodes.length];
        for (int i = keys.length - 1; i >= 0; i--) {
            keys[i] = InputUtil.Type.KEYSYM.createFromCode(modifierCodes[i]);
        }
        return keys;
    }

    public final String modGroup;
    public final String subGroup;
    public final String name;
    private final InputUtil.Key defaultTrigger;
    private final InputUtil.Key[] defaultModifiers;
    private InputUtil.Key trigger;
    private InputUtil.Key[] modifiers;
    private boolean exclusive;
    private boolean pressed;
    private int timesTyped;
    private Runnable onPressed, onReleased, onTyped;

    /**
     * Creates a new keybind from {@link GLFW} key codes.
     * @param modGroup the name or id of the mod owning this keybind
     * @param name the name of this keybind in configuration menus
     * @param triggerCode the primary trigger key for this keybind. Setting this
     * null will create a modifer-only keybind.
     * @param modifierCodes additional keys required to be pressed for this
     * binding to trigger.
     */
    public Keybind(String modGroup, String name, int triggerCode, int... modifierCodes) {
        this(modGroup, null, name, InputUtil.Type.KEYSYM.createFromCode(triggerCode), codesToKeys(modifierCodes));
    }

    /**
     * Creates a new keybind from {@link GLFW} key codes.
     * @param modGroup the name or id of the mod owning this keybind
     * @param subGroup a sub-grouping when displaying this keybind in
     * configuration menus; can be null.
     * @param name the name of this keybind in configuration menus
     * @param triggerCode the primary trigger key for this keybind. Setting this
     * null will create a modifer-only keybind.
     * @param modifierCodes additional keys required to be pressed for this
     * binding to trigger.
     */
    public Keybind(String modGroup, String subGroup, String name, int triggerCode, int... modifierCodes) {
        this(modGroup, subGroup, name, InputUtil.Type.KEYSYM.createFromCode(triggerCode), codesToKeys(modifierCodes));
    }

    /**
     * Creates a new keybind from Keys.
     * @param modGroup the name or id of the mod owning this keybind
     * @param name the name of this keybind in configuration menus
     * @param trigger the primary trigger key for this keybind. Setting this
     * null will create a modifer-only keybind.
     * @param modifiers additional keys required to be pressed for this binding
     * to trigger.
     */
    public Keybind(String modGroup, String name, InputUtil.Key trigger, InputUtil.Key... modifiers) {
        this(modGroup, null, name, trigger, modifiers);
    }

    /**
     * Creates a new keybind from Keys.
     * @param modGroup the name or id of the mod owning this keybind
     * @param subGroup a sub-grouping when displaying this keybind in
     * configuration menus; can be null.
     * @param name the name of this keybind in configuration menus
     * @param trigger the primary trigger key for this keybind. Setting this
     * null will create a modifer-only keybind.
     * @param modifiers additional keys required to be pressed for this binding
     * to trigger.
     * @throws IllegalArgumentException if the trigger key is null but no
     * modifiers are provided.
     */
    public Keybind(String modGroup, String subGroup, String name, InputUtil.Key trigger, InputUtil.Key... modifiers) {
        this.modGroup = Objects.requireNonNull(modGroup, "Mod group name cannot be null.");
        this.subGroup = subGroup;
        this.name = Objects.requireNonNull(name, "Keybind name cannot be null.");
        this.defaultTrigger = this.trigger = trigger;
        if (trigger == null && modifiers.length == 0) {
            throw new IllegalArgumentException("Keybind cannot be modifier-only (null trigger) and have an empty modifier array.");
        }
        modifiers = Arrays.copyOf(modifiers, modifiers.length);
        Arrays.sort(modifiers,
                Comparator
                        .comparing(InputUtil.Key::getCategory)
                        .thenComparingInt(InputUtil.Key::getCode));
        this.defaultModifiers = this.modifiers = modifiers;
    }

    /**
     * @return whether this keybind is set in exclusive mode.
     */
    public boolean isExclusive() {
        return exclusive;
    }

    /**
     * Sets this keybind as exclusive, only allowing it to trigger when its
     * trigger and modifiers are the only keys pressed.
     * @return this keybind.
     */
    public Keybind setExclusive() {
        this.exclusive = true;
        return this;
    }

    /**
     * Sets the callback used when this keybind is pressed according to the
     * conditions in {@link Keybind}.
     * @param onPressedCallback the keybind pressed callback.
     * @return this keybind.
     */
    public Keybind onPressed(Runnable onPressedCallback) {
        this.onPressed = onPressedCallback;
        return this;
    }

    /**
     * Sets the callback used when this keybind is released according to the
     * conditions in {@link Keybind}.
     * @param onReleasedCallback the keybind released callback.
     * @return this keybind.
     */
    public Keybind onReleased(Runnable onReleasedCallback) {
        this.onReleased = onReleasedCallback;
        return this;
    }

    /**
     * Sets the callback used when this keybind is typed according to the
     * conditions in {@link Keybind}.
     * @param onTypedCallback the keybind typed callback.
     * @return this keybind.
     */
    public Keybind onTyped(Runnable onTypedCallback) {
        this.onTyped = onTypedCallback;
        return this;
    }

    /**
     * XKCD 927 equivalent to {@link KeyBinding#getDefaultKey()}
     * @return the default key used to trigger this keybinding, or null if this
     * is a modifier-only keybind.
     */
    public InputUtil.Key getDefaultTrigger() {
        return defaultTrigger;
    }

    /**
     * @return the default modifier keys required before this keybinding can be
     * triggered
     */
    public InputUtil.Key[] getDefaultModifiers() {
        return Arrays.copyOf(defaultModifiers, defaultModifiers.length);
    }

    /**
     * @return the current key used to trigger this keybinding
     */
    public InputUtil.Key getTrigger() {
        return trigger;
    }

    /**
     * @return the current modifier keys required before this keybinding can be
     * triggered
     */
    public InputUtil.Key[] getModifiers() {
        return Arrays.copyOf(modifiers, modifiers.length);
    }

    /**
     * @return whether this is a modifier-only keybind (i.e. it has a null
     * trigger key).
     */
    public boolean isModifierOnly() {
        return trigger == null;
    }

    /**
     * Sets the trigger and modifiers for this keybind. If this is a
     * modifier-only keybind and a trigger is present, it will be moved to the
     * modifier array to maintain the keybind's modifier-only state.
     * @param trigger the primary trigger key for this keybind, or the last key
     * pressed by the user when changing the keybind.
     * @param modifiers additional keys required to be pressed for this binding
     * to trigger.
     * @throws IllegalArgumentException if the keybind is triggered but no
     * trigger key is provided, or if it is modifier-only and no keys are
     * provided.
     */
    public void setBinding(InputUtil.Key trigger, InputUtil.Key... modifiers) {
        if (isModifierOnly()) {
            if (modifiers.length == 0 && trigger == null) {
                throw new IllegalArgumentException("Modifier-only keybind cannot have an empty modifier array.");
            }
            if (trigger != null) {
                modifiers = Arrays.copyOf(modifiers, modifiers.length + 1);
                modifiers[modifiers.length - 1] = trigger;
            } else {
                modifiers = Arrays.copyOf(modifiers, modifiers.length);
            }
        } else {
            if (trigger == null) {
                throw new IllegalArgumentException("Triggered keybind cannot have null trigger.");
            }
            this.trigger = trigger;
            modifiers = Arrays.copyOf(modifiers, modifiers.length);
        }
        Arrays.sort(modifiers,
                Comparator.comparing(InputUtil.Key::getCategory).thenComparingInt(InputUtil.Key::getCode));
        this.modifiers = modifiers;
    }

    /**
     * XKCD 927 equivalent to {@link KeyBinding#isDefault()}
     * @return whether the current modifiers and trigger match the defaults.
     */
    public boolean isDefault() {
        return trigger.equals(defaultTrigger) && Arrays.equals(modifiers, defaultModifiers);
    }

    /**
     * Sets the current trigger and modifiers to the hardcoded defaults.
     */
    public void restoreDefaults() {
        this.trigger = defaultTrigger;
        this.modifiers = defaultModifiers;
    }

    /**
     * XKCD 927 equivalent to {@link KeyBinding#isPressed()}
     * @return whether this keybinding is currently considered to be pressed by
     * the user, based both on the physical state of keys pressed and whether
     * this keybind is configured as inclusive or exclusive.
     */
    public boolean isPressed() {
        return pressed;
    }

    void setPressed(boolean pressed, boolean allowCallback) {
        allowCallback &= this.pressed != pressed;
        this.pressed = pressed;
        if (pressed) {
            if (allowCallback && onPressed != null) {
                onPressed.run();
            }
        } else {
            if (allowCallback && onReleased != null) {
                onReleased.run();
            }
        }
    }

    /**
     * The number of times the keybind has been typed is counted, calling this
     * consumes one count.<br>
     * XKCD 927 equivalent to {@link KeyBinding#wasPressed()}
     * @return whether the keybind has been typed.
     */
    public boolean wasTyped() {
        if (this.timesTyped <= 0) {
            return false;
        }
        this.timesTyped--;
        return true;
    }

    void addTyped(boolean allowCallback) {
        this.timesTyped++;
        if (allowCallback && onTyped != null) {
            onTyped.run();
        }
    }

    /**
     * Sets the keybind's pressed state to false and typed counter to 0 without
     * triggering event callbacks.
     */
    public void clearState() {
        this.pressed = false;
        this.timesTyped = 0;
    }

    boolean matchesPressedKeys(Set<InputUtil.Key> keys) {
        if (trigger != null && !keys.contains(trigger)) {
            return false;
        }
        for (InputUtil.Key modKey : modifiers) {
            if (!keys.contains(modKey)) {
                return false;
            }
        }
        return exclusive || keys.size() == modifiers.length;
    }
}
