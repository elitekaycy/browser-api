package com.browserapi.controller.dto;

import java.time.LocalDateTime;

/**
 * Cache metadata included in extraction responses.
 * Helps clients understand cache status and optimize refresh strategies.
 */
public record CacheInfo(
        boolean hit,
        String cacheKey,
        LocalDateTime expiresAt
) {
    public static CacheInfo hit(String cacheKey, LocalDateTime expiresAt) {
        return new CacheInfo(true, cacheKey, expiresAt);
    }

    public static CacheInfo miss(String cacheKey, LocalDateTime expiresAt) {
        return new CacheInfo(false, cacheKey, expiresAt);
    }
}
