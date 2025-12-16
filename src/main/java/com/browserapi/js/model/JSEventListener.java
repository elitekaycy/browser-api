package com.browserapi.js.model;

/**
 * Represents a JavaScript event listener attached to an element.
 * Captured via addEventListener() tracking or DevTools API.
 */
public record JSEventListener(
        String eventType,
        String listenerCode,
        boolean useCapture,
        boolean passive
) {
    public JSEventListener(String eventType, String listenerCode) {
        this(eventType, listenerCode, false, false);
    }
}
