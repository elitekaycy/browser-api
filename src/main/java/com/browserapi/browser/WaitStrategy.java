package com.browserapi.browser;

import com.microsoft.playwright.options.WaitUntilState;

/**
 * Navigation wait strategies for different page loading scenarios.
 * Controls when Playwright considers navigation complete.
 */
public enum WaitStrategy {

    /**
     * Wait for 'load' event - fires when DOM and all resources (images, CSS, scripts) are loaded.
     * <p>
     * Best for: Static HTML pages, traditional server-rendered sites, blogs.
     * <p>
     * Performance: Balanced - faster than NETWORKIDLE, more reliable than DOMCONTENTLOADED.
     * <p>
     * This is the default and recommended strategy for most use cases.
     */
    LOAD(WaitUntilState.LOAD),

    /**
     * Wait for 'DOMContentLoaded' event - fires when HTML is parsed and DOM is ready.
     * Resources like images and stylesheets may still be loading.
     * <p>
     * Best for: Pages with heavy resources but fast DOM rendering.
     * <p>
     * Performance: Fastest option, but content may not be fully rendered.
     * <p>
     * Use when you only need DOM structure, not visual content.
     */
    DOMCONTENTLOADED(WaitUntilState.DOMCONTENTLOADED),

    /**
     * Wait for network to be idle - no network requests for at least 500ms.
     * <p>
     * Best for: Single Page Applications (SPAs), API-driven apps, AJAX-heavy pages.
     * Most reliable for ensuring dynamic content is loaded.
     * <p>
     * Performance: Slowest option - waits for all network activity to settle.
     * <p>
     * Recommended for React, Vue, Angular apps that load data after initial render.
     */
    NETWORKIDLE(WaitUntilState.NETWORKIDLE),

    /**
     * No waiting - navigation completes as soon as navigation commits.
     * Use this when you want to implement custom wait logic.
     * <p>
     * Best for: Custom wait conditions using NavigationHelper.waitForCondition().
     * <p>
     * Performance: Instant, but requires manual waiting for specific elements.
     * <p>
     * Example:
     * <pre>
     * navigationHelper.navigateWithRetry(page, url, WaitStrategy.NONE);
     * navigationHelper.waitForCondition(page, ".content-loaded", 5000);
     * </pre>
     */
    NONE(WaitUntilState.COMMIT);

    private final WaitUntilState playwrightState;

    WaitStrategy(WaitUntilState playwrightState) {
        this.playwrightState = playwrightState;
    }

    /**
     * Converts this strategy to Playwright's WaitUntilState.
     */
    public WaitUntilState toPlaywrightState() {
        return playwrightState;
    }
}
