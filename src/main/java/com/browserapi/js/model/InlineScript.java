package com.browserapi.js.model;

/**
 * Represents an inline script tag within the component.
 */
public record InlineScript(
        String content,
        String type,
        boolean isModule
) {
    public InlineScript(String content, String type) {
        this(content, type, "module".equalsIgnoreCase(type));
    }

    public InlineScript(String content) {
        this(content, "text/javascript", false);
    }
}
