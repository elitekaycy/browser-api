package com.browserapi.js.model;

/**
 * Represents an inline event handler attribute.
 * Examples: onclick, onload, onmouseover, etc.
 */
public record InlineHandler(
        String attribute,
        String code
) {
    public String getEventType() {
        if (attribute.startsWith("on")) {
            return attribute.substring(2);
        }
        return attribute;
    }
}
