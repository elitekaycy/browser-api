package com.browserapi.component.model;

/**
 * Request for a single component within a batch extraction.
 */
public record BatchComponentRequest(
        String name,
        String selector,
        boolean multiple,
        ExtractionOptions options
) {
    public BatchComponentRequest(String name, String selector) {
        this(name, selector, false, null);
    }

    public BatchComponentRequest(String name, String selector, boolean multiple) {
        this(name, selector, multiple, null);
    }
}
