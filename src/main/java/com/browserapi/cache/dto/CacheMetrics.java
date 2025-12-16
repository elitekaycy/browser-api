package com.browserapi.cache.dto;

/**
 * Cache metrics DTO for monitoring cache effectiveness.
 */
public record CacheMetrics(
        long totalEntries,
        long totalHits,
        double hitRate,
        long htmlCount,
        long cssCount,
        long jsonCount
) {
    public CacheMetrics {
        if (totalEntries < 0) {
            throw new IllegalArgumentException("Total entries cannot be negative");
        }
        if (totalHits < 0) {
            throw new IllegalArgumentException("Total hits cannot be negative");
        }
        if (hitRate < 0 || hitRate > 100) {
            throw new IllegalArgumentException("Hit rate must be between 0 and 100");
        }
    }
}
