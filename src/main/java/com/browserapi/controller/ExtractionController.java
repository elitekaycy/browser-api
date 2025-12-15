package com.browserapi.controller;

import com.browserapi.extraction.dto.ExtractionRequest;
import com.browserapi.extraction.dto.ExtractionResponse;
import com.browserapi.extraction.exception.ExtractionException;
import com.browserapi.extraction.service.ExtractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API controller for content extraction operations.
 * Provides endpoints for extracting HTML, CSS, and JSON from web pages.
 */
@RestController
@RequestMapping("/api/v1/extract")
@Tag(name = "Extraction", description = "Content extraction endpoints")
public class ExtractionController {

    private static final Logger log = LoggerFactory.getLogger(ExtractionController.class);

    private final ExtractionService extractionService;

    public ExtractionController(ExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    /**
     * Extracts content from a web page based on the request parameters.
     *
     * @param request extraction parameters (url, type, selector, options)
     * @return extraction response with data and metadata
     */
    @PostMapping
    @Operation(
            summary = "Extract content from webpage",
            description = """
                    Extracts HTML, CSS, or JSON content from a webpage using CSS selectors.

                    Supported extraction types:
                    - HTML: Extract element content (innerHTML or outerHTML)
                    - CSS: Extract computed styles (coming soon)
                    - JSON: Extract structured data (coming soon)

                    Example request:
                    {
                      "url": "https://example.com",
                      "type": "HTML",
                      "selector": ".content",
                      "waitStrategy": "LOAD",
                      "options": {
                        "includeOuter": false,
                        "cleanHTML": true
                      }
                    }
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
                                                      "data": "<h1>Title</h1><p>Content...</p>",
                                                      "type": "HTML",
                                                      "selector": ".content",
                                                      "extractionTimeMs": 245,
                                                      "metadata": {
                                                        "elementCount": 1,
                                                        "dataLength": 1024
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
    public ResponseEntity<?> extract(@RequestBody ExtractionRequest request) {
        try {
            log.info("Extraction request received: type={}, url={}, selector={}",
                    request.type(), request.url(), request.selector());

            ExtractionResponse response = extractionService.extract(request);

            log.info("Extraction completed: type={}, dataSize={}",
                    response.type(), response.getDataSize());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid extraction request: {}", e.getMessage());
            return ResponseEntity
                    .badRequest()
                    .body(new ErrorResponse("Invalid request: " + e.getMessage()));

        } catch (ExtractionException e) {
            log.error("Extraction failed: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Extraction failed: " + e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error during extraction", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Unexpected error: " + e.getMessage()));
        }
    }

    /**
     * Simple error response record.
     */
    record ErrorResponse(String error) {}
}
