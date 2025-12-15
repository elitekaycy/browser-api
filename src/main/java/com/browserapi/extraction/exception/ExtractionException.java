package com.browserapi.extraction.exception;

/**
 * Exception thrown when content extraction operations fail.
 * Wraps underlying errors (selector not found, parsing errors, etc.)
 * and provides context-specific error messages.
 */
public class ExtractionException extends RuntimeException {

    public ExtractionException(String message) {
        super(message);
    }

    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExtractionException(Throwable cause) {
        super(cause);
    }
}
