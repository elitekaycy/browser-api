package com.browserapi.browser;

import java.util.UUID;

/**
 * Manages Playwright browser lifecycle and page sessions.
 * Provides thread-safe session management with automatic cleanup and resource limits.
 *
 * <p>Implementation responsibilities:
 * <ul>
 *   <li>Initialize and maintain singleton Playwright instance</li>
 *   <li>Manage browser and page lifecycle</li>
 *   <li>Enforce maximum concurrent session limits</li>
 *   <li>Track and cleanup idle sessions</li>
 *   <li>Handle graceful shutdown</li>
 * </ul>
 */
public interface BrowserManager {

    /**
     * Creates a new browser session with a page navigated to the specified URL.
     * Uses default LOAD wait strategy.
     * Reuses the existing browser instance for performance.
     *
     * @param url the URL to navigate to
     * @return PageSession containing the Playwright page and metadata
     * @throws BrowserException if session creation fails or max sessions exceeded
     */
    PageSession createSession(String url);

    /**
     * Creates a new browser session with a page navigated to the specified URL.
     * Allows specifying custom wait strategy for different page types.
     *
     * @param url the URL to navigate to
     * @param waitStrategy the wait strategy to use (LOAD, NETWORKIDLE, etc.)
     * @return PageSession containing the Playwright page and metadata
     * @throws BrowserException if session creation fails or max sessions exceeded
     */
    PageSession createSession(String url, WaitStrategy waitStrategy);

    /**
     * Creates a new browser session with a page navigated to the specified URL.
     * Allows specifying both wait strategy and custom timeout.
     *
     * @param url the URL to navigate to
     * @param waitStrategy the wait strategy to use
     * @param timeoutMs custom timeout in milliseconds (null = use default)
     * @return PageSession containing the Playwright page and metadata
     * @throws BrowserException if session creation fails or max sessions exceeded
     */
    PageSession createSession(String url, WaitStrategy waitStrategy, Long timeoutMs);

    /**
     * Closes a specific browser session and releases its resources.
     * Safe to call multiple times for the same session ID.
     *
     * @param sessionId the unique session identifier
     */
    void closeSession(UUID sessionId);

    /**
     * Gets the current number of active sessions.
     * Useful for monitoring and debugging.
     *
     * @return count of active sessions
     */
    int getActiveSessionCount();

    /**
     * Closes all active sessions and shuts down the browser and Playwright instance.
     * Called automatically during Spring application shutdown.
     */
    void shutdown();
}
