package com.browserapi.controller;

import com.browserapi.browser.WaitStrategy;
import com.browserapi.cache.service.CacheService;
import com.browserapi.controller.dto.ApiErrorResponse;
import com.browserapi.controller.dto.ApiExtractionResponse;
import com.browserapi.controller.dto.CacheInfo;
import com.browserapi.extraction.ExtractionType;
import com.browserapi.extraction.dto.ExtractionRequest;
import com.browserapi.extraction.dto.ExtractionResponse;
import com.browserapi.extraction.exception.ExtractionException;
import com.browserapi.extraction.service.ExtractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * REST API controller for content extraction operations.
 * Provides both GET and POST endpoints for extracting HTML, CSS, and JSON from web pages.
 */
@RestController
@RequestMapping("/api/v1/extract")
@Tag(name = "Extraction", description = "Content extraction endpoints")
public class ExtractionController {

    private static final Logger log = LoggerFactory.getLogger(ExtractionController.class);

    private final ExtractionService extractionService;
    private final CacheService cacheService;

    public ExtractionController(ExtractionService extractionService, CacheService cacheService) {
        this.extractionService = extractionService;
        this.cacheService = cacheService;
    }

    /**
     * Extracts content from a web page using query parameters.
     * Suitable for simple extractions that can be bookmarked.
     *
     * @param url URL to extract from
     * @param type extraction type (HTML, CSS, JSON)
     * @param selector CSS selector
     * @param waitStrategy wait strategy (optional)
     * @param all extract all matching elements (optional)
     * @param outer include outer HTML (optional, HTML only)
     * @param clean clean HTML (optional, HTML only)
     * @param noScripts remove scripts (optional, HTML only)
     * @param noComments remove comments (optional, HTML only)
     * @param normalize normalize whitespace (optional, HTML only)
     * @param format output format (optional, CSS only)
     * @param attributes extract attributes (optional, JSON only)
     * @param includeText include text content (optional, JSON only)
     * @return extraction response with cache metadata
     */
    @GetMapping
    @Operation(
            summary = "Extract content from webpage (query parameters)",
            description = """
                    Extracts HTML, CSS, or JSON content from a webpage using CSS selectors.
                    Simple requests via query parameters - good for testing and bookmarking.

                    Supported extraction types:
                    - HTML: Extract element content (innerHTML or outerHTML)
                    - CSS: Extract computed styles
                    - JSON: Extract structured data

                    Example requests:
                    - GET /api/v1/extract?url=https://example.com&type=HTML&selector=h1
                    - GET /api/v1/extract?url=https://example.com&type=CSS&selector=h1&format=json
                    - GET /api/v1/extract?url=https://example.com&type=JSON&selector=a&attributes=true
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Extraction successful",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                      "data": "<h1>Title</h1>",
                                                      "type": "HTML",
                                                      "selector": "h1",
                                                      "extractionTimeMs": 245,
                                                      "metadata": {
                                                        "elementCount": 1,
                                                        "dataLength": 14
                                                      },
                                                      "cache": {
                                                        "hit": true,
                                                        "cacheKey": "abc123...",
                                                        "expiresAt": "2025-12-16T12:00:00"
                                                      }
                                                    }
                                                    """
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid request (bad selector, missing parameters)"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Extraction failed (navigation error, element not found)"
                    )
            }
    )
    public ResponseEntity<?> extract(
            @RequestParam String url,
            @RequestParam ExtractionType type,
            @RequestParam String selector,
            @RequestParam(name = "wait", required = false) WaitStrategy waitStrategy,
            @RequestParam(required = false) Boolean all,
            @RequestParam(required = false) Boolean outer,
            @RequestParam(required = false) Boolean clean,
            @RequestParam(name = "no_scripts", required = false) Boolean noScripts,
            @RequestParam(name = "no_comments", required = false) Boolean noComments,
            @RequestParam(required = false) Boolean normalize,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) Boolean attributes,
            @RequestParam(name = "include_text", required = false) Boolean includeText,
            HttpServletRequest servletRequest
    ) {
        try {
            log.info("GET extraction request: type={}, url={}, selector={}", type, url, selector);

            Map<String, Object> options = new HashMap<>();
            if (all != null) options.put("multiple", all);
            if (outer != null) options.put("includeOuter", outer);
            if (clean != null) options.put("cleanHTML", clean);
            if (noScripts != null) options.put("removeScripts", noScripts);
            if (noComments != null) options.put("removeComments", noComments);
            if (normalize != null) options.put("normalizeWhitespace", normalize);
            if (format != null) options.put("format", format);
            if (attributes != null) options.put("attributes", attributes);
            if (includeText != null) options.put("includeText", includeText);

            ExtractionRequest request = new ExtractionRequest(
                    url,
                    type,
                    selector,
                    waitStrategy,
                    options
            );

            return executeExtraction(request, servletRequest.getRequestURI());

        } catch (IllegalArgumentException e) {
            log.warn("Invalid GET extraction request: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(ApiErrorResponse.badRequest(
                            "Invalid request parameters",
                            e.getMessage(),
                            servletRequest.getRequestURI()
                    ));
        }
    }

    /**
     * Extracts content from a web page using JSON request body.
     * Suitable for complex extractions with nested schemas and many options.
     *
     * @param request extraction request
     * @return extraction response with cache metadata
     */
    @PostMapping
    @Operation(
            summary = "Extract content from webpage (JSON body)",
            description = """
                    Extracts HTML, CSS, or JSON content from a webpage using CSS selectors.
                    Complex requests via JSON body - good for schemas and many options.

                    Example request:
                    {
                      "url": "https://example.com",
                      "type": "JSON",
                      "selector": ".product",
                      "options": {
                        "multiple": true,
                        "schema": {
                          "title": "h2",
                          "price": ".price",
                          "rating": ".stars@data-rating"
                        }
                      }
                    }
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Extraction successful"
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid request (validation errors)"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Extraction failed"
                    )
            }
    )
    public ResponseEntity<?> extractPost(
            @RequestBody ExtractionRequest request,
            HttpServletRequest servletRequest
    ) {
        try {
            log.info("POST extraction request: type={}, url={}, selector={}",
                    request.type(), request.url(), request.selector());

            return executeExtraction(request, servletRequest.getRequestURI());

        } catch (IllegalArgumentException e) {
            log.warn("Invalid POST extraction request: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(ApiErrorResponse.badRequest(
                            "Invalid request body",
                            e.getMessage(),
                            servletRequest.getRequestURI()
                    ));
        }
    }

    private ResponseEntity<?> executeExtraction(ExtractionRequest request, String path) {
        boolean cacheHit = false;
        String cacheKey = cacheService.getCacheKey(request);
        LocalDateTime expiresAt = cacheService.calculateExpiresAt(request);

        try {
            Optional<ExtractionResponse> cached = cacheService.get(request);
            if (cached.isPresent()) {
                cacheHit = true;
                ExtractionResponse response = cached.get();
                CacheInfo cacheInfo = CacheInfo.hit(cacheKey, expiresAt);
                ApiExtractionResponse apiResponse = ApiExtractionResponse.from(response, cacheInfo);

                log.info("Cache HIT: type={}, dataSize={}", response.type(), response.getDataSize());

                return ResponseEntity.ok()
                        .cacheControl(buildCacheControl(request))
                        .body(apiResponse);
            }

            ExtractionResponse response = extractionService.extract(request);
            CacheInfo cacheInfo = CacheInfo.miss(cacheKey, expiresAt);
            ApiExtractionResponse apiResponse = ApiExtractionResponse.from(response, cacheInfo);

            log.info("Extraction completed: type={}, dataSize={}", response.type(), response.getDataSize());

            return ResponseEntity.ok()
                    .cacheControl(buildCacheControl(request))
                    .body(apiResponse);

        } catch (ExtractionException e) {
            log.error("Extraction failed: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.internalError(
                            "Extraction failed",
                            e.getMessage(),
                            path
                    ));

        } catch (Exception e) {
            log.error("Unexpected error during extraction", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiErrorResponse.internalError(
                            "Unexpected error",
                            e.getMessage(),
                            path
                    ));
        }
    }

    private CacheControl buildCacheControl(ExtractionRequest request) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = cacheService.calculateExpiresAt(request);
        long ttlSeconds = java.time.Duration.between(now, expiresAt).getSeconds();

        return CacheControl.maxAge(ttlSeconds > 0 ? ttlSeconds : 0, TimeUnit.SECONDS)
                .cachePublic();
    }
}
