package com.caucraft.shadowmap.api.util;

/**
 * An enum indicating the result of an event handler and whether other event
 * handlers should be allowed to accept the event.
 */
public enum EventResult {
    /**
     * Indicates the event handler successfully handled or ignored the event and
     * other handlers should be allowed to accept the event.
     */
    PASS,
    /**
     * Indicates the event handler handled the event and other handlers should
     * not be allowed to accept the event.
     */
    CONSUME,
}
