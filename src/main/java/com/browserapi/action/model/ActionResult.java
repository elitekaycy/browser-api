package com.browserapi.action.model;

import java.time.LocalDateTime;

/**
 * Result of executing a browser action.
 */
public record ActionResult(
        Action action,
        boolean success,
        String error,
        long executionTimeMs,
        String screenshotBase64,
        String pageUrl,
        LocalDateTime executedAt
) {
    /**
     * Create a successful result.
     */
    public static ActionResult success(Action action, long executionTimeMs, String pageUrl) {
        return new ActionResult(
                action,
                true,
                null,
                executionTimeMs,
                null,
                pageUrl,
                LocalDateTime.now()
        );
    }

    /**
     * Create a successful result with screenshot.
     */
    public static ActionResult success(Action action, long executionTimeMs, String pageUrl, String screenshot) {
        return new ActionResult(
                action,
                true,
                null,
                executionTimeMs,
                screenshot,
                pageUrl,
                LocalDateTime.now()
        );
    }

    /**
     * Create a failed result.
     */
    public static ActionResult failure(Action action, long executionTimeMs, String error) {
        return new ActionResult(
                action,
                false,
                error,
                executionTimeMs,
                null,
                null,
                LocalDateTime.now()
        );
    }

    /**
     * Create a failed result with page URL.
     */
    public static ActionResult failure(Action action, long executionTimeMs, String error, String pageUrl) {
        return new ActionResult(
                action,
                false,
                error,
                executionTimeMs,
                null,
                pageUrl,
                LocalDateTime.now()
        );
    }
}
