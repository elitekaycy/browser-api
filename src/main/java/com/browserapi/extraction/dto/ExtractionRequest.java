package com.browserapi.extraction.dto;

import com.browserapi.browser.WaitStrategy;
import com.browserapi.extraction.ExtractionType;

import java.util.Map;

/**
 * Request object for content extraction operations.
 * Immutable record with validation.
 *
 * @param url URL to extract content from
 * @param type type of extraction (HTML, CSS, JSON)
 * @param selector CSS selector to target specific elements
 * @param waitStrategy strategy for waiting for page load (default: LOAD)
 * @param options extractor-specific options (e.g., includeOuter, cleanHTML)
 */
public record ExtractionRequest(
        String url,
        ExtractionType type,
        String selector,
        WaitStrategy waitStrategy,
        Map<String, Object> options
) {

    /**
     * Constructor with defaults for optional parameters.
     */
    public ExtractionRequest(String url, ExtractionType type, String selector) {
        this(url, type, selector, WaitStrategy.LOAD, Map.of());
    }

    /**
     * Constructor with wait strategy but no options.
     */
    public ExtractionRequest(String url, ExtractionType type, String selector, WaitStrategy waitStrategy) {
        this(url, type, selector, waitStrategy, Map.of());
    }

    /**
     * Compact constructor with validation.
     */
    public ExtractionRequest {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be null or blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Extraction type is required");
        }
        if (selector == null || selector.isBlank()) {
            throw new IllegalArgumentException("Selector cannot be null or blank");
        }
        if (waitStrategy == null) {
            waitStrategy = WaitStrategy.LOAD;
        }
        if (options == null) {
            options = Map.of();
        }
    }

    /**
     * Gets an option value with type casting.
     *
     * @param key option key
     * @param defaultValue default value if option not present
     * @return option value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getOption(String key, T defaultValue) {
        return (T) options.getOrDefault(key, defaultValue);
    }

    /**
     * Checks if an option exists.
     */
    public boolean hasOption(String key) {
        return options.containsKey(key);
    }
}
