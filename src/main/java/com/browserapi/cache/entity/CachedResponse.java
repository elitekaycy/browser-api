package com.browserapi.cache.entity;

import com.browserapi.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Entity representing a cached extraction response.
 * Stores extraction results with TTL-based expiration for performance optimization.
 * <p>
 * Cache key is an MD5 hash of (url + type + selector + options) for O(1) lookups.
 * Expired entries are cleaned up by scheduled job.
 */
@Entity
@Table(
        name = "cached_responses",
        indexes = {
                @Index(name = "idx_cache_key", columnList = "cacheKey"),
                @Index(name = "idx_expires_at", columnList = "expiresAt"),
                @Index(name = "idx_extraction_type", columnList = "extractionType")
        }
)
public class CachedResponse extends BaseEntity {

    @Column(nullable = false, length = 32)
    private String cacheKey;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false, length = 10)
    private String extractionType;

    @Column(nullable = false, length = 500)
    private String selector;

    @Column(columnDefinition = "TEXT")
    private String data;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Long hits = 0L;

    public CachedResponse() {
    }

    public CachedResponse(String cacheKey, String url, String extractionType,
                          String selector, String data, String metadata,
                          LocalDateTime expiresAt) {
        this.cacheKey = cacheKey;
        this.url = url;
        this.extractionType = extractionType;
        this.selector = selector;
        this.data = data;
        this.metadata = metadata;
        this.expiresAt = expiresAt;
        this.hits = 0L;
    }

    public void incrementHits() {
        this.hits++;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getExtractionType() {
        return extractionType;
    }

    public void setExtractionType(String extractionType) {
        this.extractionType = extractionType;
    }

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getHits() {
        return hits;
    }

    public void setHits(Long hits) {
        this.hits = hits;
    }
}
