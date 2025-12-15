package com.browserapi.extraction.strategy.impl;

import com.browserapi.browser.PageSession;
import com.browserapi.extraction.ExtractionType;
import com.browserapi.extraction.dto.ExtractionRequest;
import com.browserapi.extraction.dto.ExtractionResponse;
import com.browserapi.extraction.exception.ExtractionException;
import com.browserapi.extraction.strategy.ExtractionStrategy;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.PlaywrightException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extraction strategy for HTML content.
 * Supports innerHTML/outerHTML extraction with multiple elements and cleaning options.
 * <p>
 * Options:
 * <ul>
 *   <li>includeOuter (boolean, default: false) - Use outerHTML instead of innerHTML</li>
 *   <li>multiple (boolean, default: false) - Extract all matching elements</li>
 *   <li>cleanHTML (boolean, default: false) - Enable HTML cleaning</li>
 *   <li>removeScripts (boolean, default: true) - Remove script tags when cleaning</li>
 *   <li>removeComments (boolean, default: false) - Remove HTML comments when cleaning</li>
 *   <li>normalizeWhitespace (boolean, default: false) - Collapse multiple spaces when cleaning</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * {@code
 * // Basic extraction
 * ExtractionRequest request = new ExtractionRequest(url, ExtractionType.HTML, ".content");
 *
 * // With outerHTML
 * ExtractionRequest request = new ExtractionRequest(
 *     url, ExtractionType.HTML, ".header",
 *     WaitStrategy.LOAD,
 *     Map.of("includeOuter", true)
 * );
 *
 * // Multiple elements with cleaning
 * ExtractionRequest request = new ExtractionRequest(
 *     url, ExtractionType.HTML, ".product",
 *     WaitStrategy.LOAD,
 *     Map.of("multiple", true, "cleanHTML", true)
 * );
 * }
 * </pre>
 */
@Component
public class HTMLExtractor implements ExtractionStrategy {

    private static final Logger log = LoggerFactory.getLogger(HTMLExtractor.class);

    @Override
    public ExtractionType getType() {
        return ExtractionType.HTML;
    }

    @Override
    public ExtractionResponse extract(ExtractionRequest request, PageSession session) {
        long startTime = System.currentTimeMillis();

        log.debug("Extracting HTML: selector={}, url={}",
                request.selector(), request.url());

        // Parse options
        boolean includeOuter = request.getOption("includeOuter", false);
        boolean multiple = request.getOption("multiple", false);
        boolean cleanHTML = request.getOption("cleanHTML", false);

        try {
            // Find elements using Playwright locator
            Locator locator = session.page().locator(request.selector());
            int elementCount = locator.count();

            log.debug("Found {} element(s) for selector: {}", elementCount, request.selector());

            // Validate element exists
            if (elementCount == 0) {
                throw new ExtractionException(
                        "No elements found for selector '%s' on page %s"
                                .formatted(request.selector(), request.url())
                );
            }

            // Extract HTML based on options
            String html;
            if (multiple) {
                html = extractMultiple(locator, includeOuter, elementCount);
            } else {
                html = extractSingle(locator, includeOuter);
            }

            // Clean HTML if requested
            if (cleanHTML) {
                html = cleanHTML(html, request);
                log.debug("HTML cleaned: removeScripts={}, removeComments={}, normalizeWhitespace={}",
                        request.getOption("removeScripts", true),
                        request.getOption("removeComments", false),
                        request.getOption("normalizeWhitespace", false));
            }

            // Build metadata
            long extractionTime = System.currentTimeMillis() - startTime;
            Map<String, Object> metadata = buildMetadata(
                    elementCount,
                    includeOuter,
                    multiple,
                    cleanHTML,
                    html.length()
            );

            log.info("HTML extraction completed: selector={}, elements={}, size={}, time={}ms",
                    request.selector(), elementCount, html.length(), extractionTime);

            return new ExtractionResponse(
                    html,
                    ExtractionType.HTML,
                    request.selector(),
                    extractionTime,
                    metadata
            );

        } catch (PlaywrightException e) {
            log.error("Playwright error during HTML extraction: selector={}, error={}",
                    request.selector(), e.getMessage());

            if (e.getMessage().contains("Invalid selector")) {
                throw new ExtractionException(
                        "Invalid CSS selector: " + request.selector(),
                        e
                );
            }

            throw new ExtractionException(
                    "Failed to extract HTML for selector '%s': %s"
                            .formatted(request.selector(), e.getMessage()),
                    e
            );

        } catch (ExtractionException e) {
            throw e;

        } catch (Exception e) {
            log.error("Unexpected error during HTML extraction: selector={}", request.selector(), e);
            throw new ExtractionException(
                    "Unexpected error during HTML extraction: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Extracts HTML from a single element (first match).
     */
    private String extractSingle(Locator locator, boolean includeOuter) {
        if (includeOuter) {
            // outerHTML - includes the wrapper element
            return locator.first().evaluate("el => el.outerHTML").toString();
        } else {
            // innerHTML - content only (default)
            return locator.first().innerHTML();
        }
    }

    /**
     * Extracts HTML from all matching elements.
     * Concatenates all elements' HTML with newlines.
     */
    private String extractMultiple(Locator locator, boolean includeOuter, int count) {
        StringBuilder combined = new StringBuilder();

        for (int i = 0; i < count; i++) {
            String html;
            if (includeOuter) {
                html = locator.nth(i).evaluate("el => el.outerHTML").toString();
            } else {
                html = locator.nth(i).innerHTML();
            }

            combined.append(html);

            // Add newline between elements (except last)
            if (i < count - 1) {
                combined.append("\n");
            }
        }

        return combined.toString();
    }

    /**
     * Cleans HTML based on request options.
     * Removes scripts, comments, and normalizes whitespace as requested.
     */
    private String cleanHTML(String html, ExtractionRequest request) {
        String cleaned = html;

        // Remove script tags (default: true)
        if (request.getOption("removeScripts", true)) {
            cleaned = cleaned.replaceAll("(?i)<script[^>]*>.*?</script>", "");
        }

        // Remove HTML comments (default: false)
        if (request.getOption("removeComments", false)) {
            cleaned = cleaned.replaceAll("<!--.*?-->", "");
        }

        // Normalize whitespace (default: false)
        if (request.getOption("normalizeWhitespace", false)) {
            // Replace multiple spaces/newlines with single space
            cleaned = cleaned.replaceAll("\\s+", " ").trim();
        }

        return cleaned;
    }

    /**
     * Builds metadata map for the extraction response.
     */
    private Map<String, Object> buildMetadata(
            int elementCount,
            boolean includeOuter,
            boolean multiple,
            boolean cleaned,
            int dataLength
    ) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("elementCount", elementCount);
        metadata.put("includeOuter", includeOuter);
        metadata.put("multiple", multiple);
        metadata.put("cleaned", cleaned);
        metadata.put("dataLength", dataLength);
        metadata.put("selectorMatched", true);
        return metadata;
    }
}
