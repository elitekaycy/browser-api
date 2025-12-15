package com.browserapi.extraction.dto;

import com.browserapi.extraction.ExtractionType;

import java.util.Map;

/**
 * Response object containing extracted content and metadata.
 * Immutable record returned by all extraction operations.
 *
 * @param data extracted content (HTML, CSS, JSON as string)
 * @param type type of extraction performed
 * @param selector CSS selector used for extraction
 * @param extractionTimeMs time taken for extraction in milliseconds
 * @param metadata additional context (element count, size, cache status, etc.)
 */
public record ExtractionResponse(
        String data,
        ExtractionType type,
        String selector,
        long extractionTimeMs,
        Map<String, Object> metadata
) {

    /**
     * Constructor with minimal parameters.
     */
    public ExtractionResponse(String data, ExtractionType type, String selector, long extractionTimeMs) {
        this(data, type, selector, extractionTimeMs, Map.of());
    }

    /**
     * Compact constructor with validation and defaults.
     */
    public ExtractionResponse {
        if (data == null) {
            data = "";
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }

    /**
     * Gets metadata value with type casting.
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, T defaultValue) {
        return (T) metadata.getOrDefault(key, defaultValue);
    }

    /**
     * Gets the size of extracted data in bytes.
     */
    public int getDataSize() {
        return data.getBytes().length;
    }

    /**
     * Checks if extraction was successful (has data).
     */
    public boolean hasData() {
        return data != null && !data.isEmpty();
    }
}
