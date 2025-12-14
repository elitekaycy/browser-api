package com.browserapi.browser;

import com.microsoft.playwright.Page;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Wrapper around Playwright Page with session metadata.
 * Provides tracking for session lifecycle, idle timeout detection, and safe cleanup.
 *
 * @param sessionId unique identifier for this session
 * @param page underlying Playwright page object
 * @param url the URL this session was created for
 * @param createdAt when this session was created
 * @param lastAccessedAt when this session was last used
 */
public record PageSession(
    UUID sessionId,
    Page page,
    String url,
    LocalDateTime createdAt,
    LocalDateTime lastAccessedAt
) {

    /**
     * Creates a new PageSession with current timestamp.
     */
    public PageSession(Page page, String url) {
        this(UUID.randomUUID(), page, url, LocalDateTime.now(), LocalDateTime.now());
    }

    /**
     * Updates the last accessed timestamp.
     * Used to track session activity for idle timeout detection.
     *
     * @return new PageSession with updated lastAccessedAt
     */
    public PageSession touch() {
        return new PageSession(sessionId, page, url, createdAt, LocalDateTime.now());
    }

    /**
     * Checks if this session has been idle for longer than the specified timeout.
     *
     * @param idleTimeoutMs idle timeout in milliseconds
     * @return true if session is idle beyond timeout
     */
    public boolean isIdleFor(long idleTimeoutMs) {
        long idleTimeMs = java.time.Duration.between(lastAccessedAt, LocalDateTime.now()).toMillis();
        return idleTimeMs > idleTimeoutMs;
    }

    /**
     * Safely closes the underlying Playwright page.
     * Handles already-closed pages gracefully.
     */
    public void close() {
        try {
            if (page != null && !page.isClosed()) {
                page.close();
            }
        } catch (Exception e) {
            // Page already closed or browser context closed - ignore
        }
    }

    /**
     * Checks if the underlying page is still open.
     */
    public boolean isOpen() {
        return page != null && !page.isClosed();
    }
}
