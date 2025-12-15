package com.browserapi.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Navigation utility with retry logic and flexible wait strategies.
 * Handles the unpredictable nature of web page loading with automatic retries,
 * exponential backoff, and detailed error reporting.
 */
@Component
public class NavigationHelper {

    private static final Logger log = LoggerFactory.getLogger(NavigationHelper.class);

    private static final int MAX_RETRIES = 3;
    private static final long BASE_RETRY_DELAY_MS = 1000; // 1 second

    /**
     * Navigates to URL with default LOAD wait strategy and retry logic.
     *
     * @param page the Playwright page
     * @param url the URL to navigate to
     * @throws BrowserException if navigation fails after all retries
     */
    public void navigateWithRetry(Page page, String url) {
        navigateWithRetry(page, url, WaitStrategy.LOAD, null);
    }

    /**
     * Navigates to URL with specified wait strategy and retry logic.
     *
     * @param page the Playwright page
     * @param url the URL to navigate to
     * @param waitStrategy the wait strategy to use
     * @throws BrowserException if navigation fails after all retries
     */
    public void navigateWithRetry(Page page, String url, WaitStrategy waitStrategy) {
        navigateWithRetry(page, url, waitStrategy, null);
    }

    /**
     * Navigates to URL with specified wait strategy, custom timeout, and retry logic.
     *
     * @param page the Playwright page
     * @param url the URL to navigate to
     * @param waitStrategy the wait strategy to use
     * @param timeoutMs custom timeout in milliseconds (null = use page default)
     * @throws BrowserException if navigation fails after all retries
     */
    public void navigateWithRetry(Page page, String url, WaitStrategy waitStrategy, Long timeoutMs) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be null or blank");
        }
        if (waitStrategy == null) {
            waitStrategy = WaitStrategy.LOAD;
        }

        int attempt = 0;
        Exception lastException = null;
        long startTime = System.currentTimeMillis();

        while (attempt < MAX_RETRIES) {
            try {
                attempt++;
                log.debug("Navigation attempt {}/{} for URL: {} (strategy: {})",
                        attempt, MAX_RETRIES, url, waitStrategy);

                navigate(page, url, waitStrategy, timeoutMs);

                long duration = System.currentTimeMillis() - startTime;
                log.info("Successfully navigated to {} on attempt {} ({}ms, strategy: {})",
                        url, attempt, duration, waitStrategy);
                return;

            } catch (TimeoutError e) {
                lastException = e;
                long attemptDuration = System.currentTimeMillis() - startTime;
                log.warn("Navigation timeout on attempt {}/{} for {} after {}ms (strategy: {})",
                        attempt, MAX_RETRIES, url, attemptDuration, waitStrategy);

                if (attempt < MAX_RETRIES) {
                    long delayMs = BASE_RETRY_DELAY_MS * (1L << (attempt - 1));
                    log.debug("Retrying in {}ms...", delayMs);
                    sleep(delayMs);
                }

            } catch (Exception e) {
                lastException = e;
                log.error("Navigation failed on attempt {}/{} for {} with unexpected error: {}",
                        attempt, MAX_RETRIES, url, e.getMessage());

                // Don't retry on non-timeout errors (DNS failures, invalid URLs, etc.)
                break;
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        String errorMessage = buildErrorMessage(url, waitStrategy, attempt, totalDuration, lastException);
        throw new BrowserException(errorMessage, lastException);
    }

    /**
     * Waits for a specific selector to appear on the page.
     * Useful for custom wait conditions after navigation with WaitStrategy.NONE.
     *
     * @param page the Playwright page
     * @param selector CSS selector to wait for
     * @param timeoutMs timeout in milliseconds
     * @throws BrowserException if element does not appear within timeout
     */
    public void waitForCondition(Page page, String selector, long timeoutMs) {
        if (selector == null || selector.isBlank()) {
            throw new IllegalArgumentException("Selector cannot be null or blank");
        }

        try {
            log.debug("Waiting for selector '{}' with timeout {}ms", selector, timeoutMs);
            long startTime = System.currentTimeMillis();

            page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                    .setTimeout(timeoutMs));

            long duration = System.currentTimeMillis() - startTime;
            log.debug("Selector '{}' appeared after {}ms", selector, duration);

        } catch (TimeoutError e) {
            throw new BrowserException(
                    "Element '%s' did not appear within %dms".formatted(selector, timeoutMs),
                    e
            );
        } catch (Exception e) {
            throw new BrowserException(
                    "Failed to wait for element '%s': %s".formatted(selector, e.getMessage()),
                    e
            );
        }
    }

    /**
     * Performs the actual navigation with specified strategy and timeout.
     */
    private void navigate(Page page, String url, WaitStrategy waitStrategy, Long timeoutMs) {
        Page.NavigateOptions options = new Page.NavigateOptions()
                .setWaitUntil(waitStrategy.toPlaywrightState());

        if (timeoutMs != null) {
            options.setTimeout(timeoutMs);
        }

        page.navigate(url, options);
    }

    /**
     * Builds a detailed error message for failed navigation.
     */
    private String buildErrorMessage(String url, WaitStrategy strategy, int attempts,
                                      long totalDuration, Exception lastException) {
        StringBuilder message = new StringBuilder();
        message.append("Failed to navigate to ").append(url);
        message.append(" after ").append(attempts).append(" attempt(s)");
        message.append("\n  - Total time: ").append(totalDuration).append("ms");
        message.append("\n  - Wait strategy: ").append(strategy);

        if (lastException != null) {
            message.append("\n  - Last error: ").append(lastException.getMessage());
        }

        message.append("\n  - Possible causes:");
        message.append("\n    * Server is too slow or unresponsive");
        message.append("\n    * URL is blocked by firewall or network");
        message.append("\n    * Page requires different wait strategy");

        message.append("\n  - Suggestions:");
        if (strategy == WaitStrategy.LOAD) {
            message.append("\n    * Try WaitStrategy.DOMCONTENTLOADED for faster loading");
            message.append("\n    * Try WaitStrategy.NETWORKIDLE for dynamic content");
        } else if (strategy == WaitStrategy.NETWORKIDLE) {
            message.append("\n    * Try WaitStrategy.LOAD for faster but less reliable loading");
        }
        message.append("\n    * Increase timeout in configuration");
        message.append("\n    * Check if URL is accessible from your network");

        return message.toString();
    }

    /**
     * Sleeps for specified milliseconds, handling interruptions.
     */
    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Sleep interrupted during navigation retry");
        }
    }
}
