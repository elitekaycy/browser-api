package com.browserapi.controller.dto;

import java.time.LocalDateTime;

/**
 * Standardized error response for all API endpoints.
 * Provides consistent error format with detailed information.
 */
public record ApiErrorResponse(
        String error,
        String message,
        String details,
        LocalDateTime timestamp,
        String path
) {
    public ApiErrorResponse(String error, String message, String details, String path) {
        this(error, message, details, LocalDateTime.now(), path);
    }

    public static ApiErrorResponse badRequest(String message, String details, String path) {
        return new ApiErrorResponse("Bad Request", message, details, path);
    }

    public static ApiErrorResponse internalError(String message, String details, String path) {
        return new ApiErrorResponse("Internal Server Error", message, details, path);
    }

    public static ApiErrorResponse notFound(String message, String details, String path) {
        return new ApiErrorResponse("Not Found", message, details, path);
    }
}
