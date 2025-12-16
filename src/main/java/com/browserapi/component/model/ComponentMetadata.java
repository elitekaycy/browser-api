package com.browserapi.component.model;

import java.time.Instant;

/**
 * Metadata about the extracted component.
 */
public record ComponentMetadata(
        String sourceUrl,
        String selector,
        Instant extractedAt,
        int totalAssets,
        long totalSize,
        ExtractionOptions options
) {
    public ComponentMetadata(
            String sourceUrl,
            String selector,
            int totalAssets,
            long totalSize,
            ExtractionOptions options
    ) {
        this(sourceUrl, selector, Instant.now(), totalAssets, totalSize, options);
    }
}
