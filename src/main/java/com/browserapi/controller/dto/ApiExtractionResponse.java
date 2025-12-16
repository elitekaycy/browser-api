package com.browserapi.controller.dto;

import com.browserapi.extraction.ExtractionType;
import com.browserapi.extraction.dto.ExtractionResponse;

import java.util.Map;

/**
 * API response wrapper for extraction operations.
 * Includes extraction data and cache metadata.
 */
public record ApiExtractionResponse(
        String data,
        ExtractionType type,
        String selector,
        long extractionTimeMs,
        Map<String, Object> metadata,
        CacheInfo cache
) {
    public static ApiExtractionResponse from(ExtractionResponse response, CacheInfo cacheInfo) {
        return new ApiExtractionResponse(
                response.data(),
                response.type(),
                response.selector(),
                response.extractionTimeMs(),
                response.metadata(),
                cacheInfo
        );
    }

    public int getDataSize() {
        return data.getBytes().length;
    }
}
