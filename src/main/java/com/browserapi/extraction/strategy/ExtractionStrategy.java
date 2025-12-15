package com.browserapi.extraction.strategy;

import com.browserapi.browser.PageSession;
import com.browserapi.extraction.ExtractionType;
import com.browserapi.extraction.dto.ExtractionRequest;
import com.browserapi.extraction.dto.ExtractionResponse;
import com.browserapi.extraction.exception.ExtractionException;

/**
 * Strategy interface for different types of content extraction.
 * Each implementation handles one extraction type (HTML, CSS, JSON).
 * <p>
 * Implementations are auto-discovered by Spring and registered in ExtractionService.
 * <p>
 * Example implementation:
 * <pre>
 * {@code
 * @Component
 * public class HTMLExtractor implements ExtractionStrategy {
 *     @Override
 *     public ExtractionResponse extract(ExtractionRequest request, PageSession session) {
 *         // Extract HTML from page
 *         String html = session.page().querySelector(request.selector()).innerHTML();
 *         return new ExtractionResponse(html, ExtractionType.HTML, request.selector(), 0);
 *     }
 *
 *     @Override
 *     public ExtractionType getType() {
 *         return ExtractionType.HTML;
 *     }
 * }
 * }
 * </pre>
 */
public interface ExtractionStrategy {

    /**
     * Extracts content from the page session based on the request.
     * <p>
     * The PageSession is already navigated to the URL with the appropriate wait strategy.
     * The implementation should:
     * <ol>
     *   <li>Validate the request is compatible with this strategy</li>
     *   <li>Execute extraction logic using the page session</li>
     *   <li>Build and return ExtractionResponse with data and metadata</li>
     * </ol>
     *
     * @param request extraction parameters (selector, options, etc.)
     * @param session active browser page session (already navigated)
     * @return extraction response with data and metadata
     * @throws ExtractionException if extraction fails (selector not found, invalid data, etc.)
     */
    ExtractionResponse extract(ExtractionRequest request, PageSession session);

    /**
     * Returns the extraction type this strategy handles.
     * Used by ExtractionService to register strategies in the type registry.
     *
     * @return the extraction type (HTML, CSS, JSON)
     */
    ExtractionType getType();

    /**
     * Validates if this strategy can handle the given request.
     * Default implementation checks if request type matches strategy type.
     * <p>
     * Override this method to add additional validation logic.
     *
     * @param request the extraction request to validate
     * @return true if this strategy can handle the request
     */
    default boolean canHandle(ExtractionRequest request) {
        return request.type() == getType();
    }
}
