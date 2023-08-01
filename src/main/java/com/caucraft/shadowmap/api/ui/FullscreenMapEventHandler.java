package com.caucraft.shadowmap.api.ui;

import com.caucraft.shadowmap.api.util.EventResult;
import com.caucraft.shadowmap.api.util.RenderArea;

/**
 * Handler for open, close, click, and other user interaction events triggered
 * in the fullscreen map view.
 */
public interface FullscreenMapEventHandler {
    /**
     * Called whenever a map screen is opened and initialized by the UI
     * renderer.
     * @param screen the active map screen.
     */
    default void mapOpened(MapScreenApi screen) {}

    /**
     * Called after the map screen has been resized (by resizing the game
     * window)
     * @param screen the active map screen.
     */
    default void mapResized(MapScreenApi screen) {}

    /**
     * Called as the map screen is being closed.
     * @param screen the active map screen.
     */
    default void mapClosed(MapScreenApi screen) {}

    /**
     * Called when the regions being rendered by the map change. Useful for
     * caching objects visible on screen.
     * @param screen the active map screen.
     * @param oldView the previous area visible on the map.
     * @param newView the new area visible on the map.
     */
    default void mapViewChanged(MapScreenApi screen, RenderArea oldView, RenderArea newView) {}

    /**
     * Called when a new key is pressed. Consuming this event will claim the
     * corresponding keyReleased event so that it only fires for this handler.
     * If a keyReleased event is required (ex. to reset some state), then this
     * event should be consumed.
     * @param screen the active map screen.
     * @param keyCode the named key code of the event as described in the GLFW
     * class
     * @param scanCode the unique/platform-specific scan code of the keyboard
     * input
     * @param modifiers a GLFW bitfield describing the modifier keys that are
     * held down (see GLFW Modifier key flags )
     * @return an {@link EventResult} indicating whether to consume or pass this
     * event. A null return is treated as {@link EventResult#PASS}.
     */
    default EventResult keyPressed(MapScreenApi screen, int keyCode, int scanCode, int modifiers) {
        return EventResult.PASS;
    }

    /**
     * Called when a key is released. If the corresponding keyPressed was
     * consumed, only the handler that consumed that event will receive this
     * one.
     * @param screen the active map screen.
     * @param keyCode the named key code of the event as described in the GLFW
     * class
     * @param scanCode the unique/platform-specific scan code of the keyboard
     * input
     * @param modifiers a GLFW bitfield describing the modifier keys that are
     * held down (see GLFW Modifier key flags )
     * @return an {@link EventResult} indicating whether to consume or pass this
     * event. A null return is treated as {@link EventResult#PASS}.
     */
    default EventResult keyReleased(MapScreenApi screen, int keyCode, int scanCode, int modifiers) {
        return EventResult.PASS;
    }

    /**
     * Called when the mouse is moved over the screen, regardless of whether a
     * button is pressed.
     * @param screen the active map screen.
     * @param mouseX the current x coordinate in ui space.
     * @param mouseY the current y coordinate in ui space.
     * @param deltaX the difference between the current and previous x
     * coordinate in ui space.
     * @param deltaY the difference between the current and previous y
     * coordinate in ui space.
     */
    default void mouseMoved(MapScreenApi screen, double mouseX, double mouseY, double deltaX, double deltaY) {}

    /**
     * Called when a new mouse button is pressed. Consuming this event will
     * claim corresponding mouseReleased, mouseClicked, and mouseDragged events
     * so that they only fire for this handler. If any of these events is
     * required alongside another of these events or after this event, then this
     * event should be consumed.
     * @param screen the active map screen.
     * @param button the button that was pressed.
     * @param mouseX x coordinate that was clicked in ui space.
     * @param mouseY y coordinate that was clicked in ui space.
     * @return an {@link EventResult} indicating whether to consume or pass this
     * event. A null return is treated as {@link EventResult#PASS}.
     */
    default EventResult mousePressed(MapScreenApi screen, int button, double mouseX, double mouseY) {
        return EventResult.PASS;
    }

    /**
     * Called when a mouse button is released. If the corresponding mousePressed
     * was consumed, only the handler that consumed that event will receive this
     * one.
     * @param screen the active map screen.
     * @param button the button that was pressed.
     * @param mouseX x coordinate that was clicked in ui space.
     * @param mouseY y coordinate that was clicked in ui space.
     * @return an {@link EventResult} indicating whether to consume or pass this
     * event. A null return is treated as {@link EventResult#PASS}.
     */
    default EventResult mouseReleased(MapScreenApi screen, int button, double mouseX, double mouseY) {
        return EventResult.PASS;
    }

    /**
     * Called only when a mouse button is both pressed and released with the
     * same button, the mouse has not moved more than some threshold from the
     * initial press position, and either the corresponding press and release
     * were not consumed or this handler consumed the previous event(s).
     * @param screen the active map screen.
     * @param button the button that was pressed.
     * @param mouseX x coordinate that was clicked in ui space.
     * @param mouseY y coordinate that was clicked in ui space.
     * @return an {@link EventResult} indicating whether to consume or pass this
     * event. A null return is treated as {@link EventResult#PASS}.
     */
    default EventResult mouseClicked(MapScreenApi screen, int button, double mouseX, double mouseY) {
        return EventResult.PASS;
    }

    /**
     * Called when the mouse is dragged over the screen while a button is
     * pressed. If the corresponding mousePressed was consumed, only the handler
     * that consumed that event will receive this one. This event will fire for
     * every mouse button pressed, following the above conditions for each
     * button.
     * @param screen the active map screen.
     * @param mouseX the current x coordinate in ui space.
     * @param mouseY the current y coordinate in ui space.
     * @param deltaX the difference between the current and previous x
     * coordinate in ui space.
     * @param deltaY the difference between the current and previous y
     * coordinate in ui space.
     * @return an {@link EventResult} indicating whether to consume or pass this
     * event. A null return is treated as {@link EventResult#PASS}.
     */
    default EventResult mouseDragged(MapScreenApi screen, int button, double mouseX, double mouseY, double deltaX, double deltaY) {
        return EventResult.PASS;
    }

    /**
     * Called when the mouse wheel is scrolled.
     * @param screen the active map screen.
     * @param mouseX the current x coordinate in ui space.
     * @param mouseY the current y coordinate in ui space.
     * @param amount the amount the mouse wheel was scrolled. Values less than 0
     * indicate scroll down, greater than 0 indicate scroll up.
     * @return an {@link EventResult} indicating whether to consume or pass this
     * event. A null return is treated as {@link EventResult#PASS}.
     */
    default EventResult mouseScrolled(MapScreenApi screen, double mouseX, double mouseY, double amount) {
        return EventResult.PASS;
    }
}
