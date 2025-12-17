package com.browserapi.component.entity;

import com.browserapi.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Entity representing a cached component extraction.
 * Stores complete component data with dedicated TTL and size limits.
 * <p>
 * Key differences from CachedResponse:
 * <ul>
 *   <li>Dedicated storage for complete components</li>
 *   <li>Longer TTL configuration (components are expensive to extract)</li>
 *   <li>Size limits enforced (10MB default)</li>
 *   <li>Separate access statistics</li>
 *   <li>Format tracking (HTML, React, Vue, etc.)</li>
 * </ul>
 */
@Entity
@Table(
        name = "cached_components",
        indexes = {
                @Index(name = "idx_component_cache_key", columnList = "cacheKey"),
                @Index(name = "idx_component_expires_at", columnList = "expiresAt"),
                @Index(name = "idx_component_format", columnList = "format"),
                @Index(name = "idx_component_url", columnList = "url")
        }
)
public class CachedComponent extends BaseEntity {

    @Column(nullable = false, length = 32, unique = true)
    private String cacheKey;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false, length = 500)
    private String selector;

    @Column(nullable = false, length = 20)
    private String format; // JSON, HTML, REACT, VUE, WEB_COMPONENT

    @Column(columnDefinition = "TEXT")
    private String html;

    @Column(columnDefinition = "TEXT")
    private String css;

    @Column(columnDefinition = "TEXT")
    private String javascript;

    @Column(length = 50)
    private String namespace;

    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON serialized ComponentMetadata

    @Column(columnDefinition = "TEXT")
    private String statistics; // JSON serialized ExtractionStatistics

    @Column(columnDefinition = "TEXT")
    private String exportData; // For REACT/VUE/WEB_COMPONENT: stores all files as JSON

    @Column(nullable = false)
    private Long sizeBytes = 0L;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Long accessCount = 0L;

    @Column
    private LocalDateTime lastAccessedAt;

    public CachedComponent() {
    }

    public void incrementAccessCount() {
        this.accessCount++;
        this.lastAccessedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean exceedsSizeLimit(long maxSizeBytes) {
        return sizeBytes > maxSizeBytes;
    }

    // Getters and Setters

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

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public String getCss() {
        return css;
    }

    public void setCss(String css) {
        this.css = css;
    }

    public String getJavascript() {
        return javascript;
    }

    public void setJavascript(String javascript) {
        this.javascript = javascript;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getStatistics() {
        return statistics;
    }

    public void setStatistics(String statistics) {
        this.statistics = statistics;
    }

    public String getExportData() {
        return exportData;
    }

    public void setExportData(String exportData) {
        this.exportData = exportData;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(Long accessCount) {
        this.accessCount = accessCount;
    }

    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }
}
