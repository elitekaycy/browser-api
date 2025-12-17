package com.browserapi.component.model;

import java.util.Map;

/**
 * Container for generated iframe embed codes.
 * Provides multiple embed options for different use cases.
 */
public record EmbedCode(
        String fixed,
        String responsive,
        String minimal,
        Map<String, String> usage
) {
    /**
     * Creates embed code with all variants and usage instructions.
     */
    public static EmbedCode create(String fixed, String responsive, String minimal) {
        Map<String, String> usage = Map.of(
                "fixed", "Use for fixed-size embeds (800x600px). Works well in sidebars or specific layouts.",
                "responsive", "Use for responsive embeds. Automatically adapts to container width. Best for mobile.",
                "minimal", "Minimal iframe code. You control all styling and dimensions."
        );

        return new EmbedCode(fixed, responsive, minimal, usage);
    }

    /**
     * Gets the embed code for a specific type.
     */
    public String getCode(EmbedType type) {
        return switch (type) {
            case FIXED -> fixed;
            case RESPONSIVE -> responsive;
            case MINIMAL -> minimal;
        };
    }
}
