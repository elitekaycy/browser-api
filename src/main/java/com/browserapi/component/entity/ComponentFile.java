package com.browserapi.component.entity;

import com.browserapi.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Entity representing a hosted component file.
 * Tracks static HTML files served with unique URLs for easy sharing.
 * <p>
 * Features:
 * <ul>
 *   <li>Unique shareable URLs (e.g., /components/abc123.html)</li>
 *   <li>Auto-expiration based on TTL</li>
 *   <li>View count tracking</li>
 *   <li>File size tracking</li>
 * </ul>
 */
@Entity
@Table(
        name = "component_files",
        indexes = {
                @Index(name = "idx_file_id", columnList = "fileId", unique = true),
                @Index(name = "idx_file_expires_at", columnList = "expiresAt"),
                @Index(name = "idx_file_url", columnList = "url")
        }
)
public class ComponentFile extends BaseEntity {

    @Column(nullable = false, length = 32, unique = true)
    private String fileId; // Unique identifier for URL (e.g., abc123)

    @Column(nullable = false, length = 2048)
    private String url; // Source URL where component was extracted

    @Column(nullable = false, length = 500)
    private String selector; // CSS selector used for extraction

    @Column(nullable = false, length = 255)
    private String filePath; // Relative file path on disk (e.g., components/abc123.html)

    @Column(nullable = false, length = 255)
    private String publicUrl; // Public URL to access the file (e.g., /hosted/abc123.html)

    @Column(nullable = false)
    private Long fileSizeBytes = 0L;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Long viewCount = 0L;

    @Column
    private LocalDateTime lastViewedAt;

    @Column(length = 50)
    private String namespace; // Component namespace for reference

    @Column(length = 20)
    private String format; // HTML, REACT, VUE, etc. (currently only HTML supported)

    public ComponentFile() {
    }

    public void incrementViewCount() {
        this.viewCount++;
        this.lastViewedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    // Getters and Setters

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
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

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public LocalDateTime getLastViewedAt() {
        return lastViewedAt;
    }

    public void setLastViewedAt(LocalDateTime lastViewedAt) {
        this.lastViewedAt = lastViewedAt;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
