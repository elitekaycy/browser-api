package com.browserapi.browser;

/**
 * Exception thrown when browser operations fail.
 * Wraps Playwright exceptions and provides context-specific error messages.
 */
public class BrowserException extends RuntimeException {

    public BrowserException(String message) {
        super(message);
    }

    public BrowserException(String message, Throwable cause) {
        super(message, cause);
    }

    public BrowserException(Throwable cause) {
        super(cause);
    }
}
