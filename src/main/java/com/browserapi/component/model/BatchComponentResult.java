package com.browserapi.component.model;

import java.util.List;

/**
 * Result of extracting a single component within a batch.
 */
public record BatchComponentResult(
        String name,
        String selector,
        CompleteComponent component,
        List<CompleteComponent> components,
        int count,
        boolean success,
        String errorMessage
) {
    /**
     * Creates a successful single component result.
     */
    public static BatchComponentResult single(String name, String selector, CompleteComponent component) {
        return new BatchComponentResult(name, selector, component, null, 1, true, null);
    }

    /**
     * Creates a successful multiple components result.
     */
    public static BatchComponentResult multiple(String name, String selector, List<CompleteComponent> components) {
        return new BatchComponentResult(name, selector, null, components, components.size(), true, null);
    }

    /**
     * Creates a failed result.
     */
    public static BatchComponentResult failed(String name, String selector, String errorMessage) {
        return new BatchComponentResult(name, selector, null, null, 0, false, errorMessage);
    }

    /**
     * Returns true if this is a multiple component result.
     */
    public boolean isMultiple() {
        return components != null && !components.isEmpty();
    }
}
