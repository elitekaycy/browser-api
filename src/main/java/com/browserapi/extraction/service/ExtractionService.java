package com.browserapi.extraction.service;

import com.browserapi.browser.BrowserManager;
import com.browserapi.browser.PageSession;
import com.browserapi.extraction.ExtractionType;
import com.browserapi.extraction.dto.ExtractionRequest;
import com.browserapi.extraction.dto.ExtractionResponse;
import com.browserapi.extraction.exception.ExtractionException;
import com.browserapi.extraction.strategy.ExtractionStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates content extraction by delegating to appropriate extraction strategies.
 * Manages browser sessions and provides unified extraction API.
 * <p>
 * The service automatically discovers all ExtractionStrategy implementations via Spring DI
 * and builds a registry for O(1) strategy lookup by type.
 * <p>
 * Usage:
 * <pre>
 * {@code
 * ExtractionRequest request = new ExtractionRequest(
 *     "https://example.com",
 *     ExtractionType.HTML,
 *     ".content"
 * );
 * ExtractionResponse response = extractionService.extract(request);
 * }
 * </pre>
 */
@Service
public class ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

    private final BrowserManager browserManager;
    private final Map<ExtractionType, ExtractionStrategy> strategies;

    /**
     * Constructor with dependency injection.
     * Spring automatically injects all beans implementing ExtractionStrategy.
     *
     * @param browserManager browser session manager
     * @param strategyList all extraction strategy implementations (auto-injected)
     */
    public ExtractionService(BrowserManager browserManager, List<ExtractionStrategy> strategyList) {
        this.browserManager = browserManager;

        // Build strategy registry: ExtractionType -> Strategy
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(
                        ExtractionStrategy::getType,
                        Function.identity()
                ));

        log.info("Initialized ExtractionService with {} strategies: {}",
                strategies.size(),
                strategies.keySet());
    }

    /**
     * Extracts content from a URL based on the request parameters.
     * <p>
     * Lifecycle:
     * <ol>
     *   <li>Validate request and lookup strategy</li>
     *   <li>Create browser session and navigate to URL</li>
     *   <li>Delegate to strategy for extraction</li>
     *   <li>Close browser session (guaranteed via finally)</li>
     *   <li>Return extraction response</li>
     * </ol>
     *
     * @param request extraction parameters
     * @return extraction response with data and metadata
     * @throws ExtractionException if extraction fails
     */
    public ExtractionResponse extract(ExtractionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Extraction request cannot be null");
        }

        log.debug("Starting extraction: type={}, url={}, selector={}",
                request.type(), request.url(), request.selector());

        long startTime = System.currentTimeMillis();

        // 1. Get strategy for this extraction type
        ExtractionStrategy strategy = getStrategy(request.type());

        // 2. Validate strategy can handle request
        if (!strategy.canHandle(request)) {
            throw new ExtractionException(
                    "Strategy %s cannot handle request: %s"
                            .formatted(strategy.getClass().getSimpleName(), request)
            );
        }

        PageSession session = null;
        try {
            // 3. Create browser session and navigate
            log.debug("Creating browser session for URL: {}", request.url());
            session = browserManager.createSession(
                    request.url(),
                    request.waitStrategy()
            );

            // 4. Delegate to strategy
            log.debug("Delegating to strategy: {}", strategy.getClass().getSimpleName());
            ExtractionResponse response = strategy.extract(request, session);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Extraction completed: type={}, selector={}, duration={}ms, dataSize={}",
                    request.type(),
                    request.selector(),
                    duration,
                    response.getDataSize());

            return response;

        } catch (ExtractionException e) {
            log.error("Extraction failed: type={}, url={}, selector={}, error={}",
                    request.type(), request.url(), request.selector(), e.getMessage());
            throw e;

        } catch (Exception e) {
            log.error("Unexpected error during extraction: type={}, url={}",
                    request.type(), request.url(), e);
            throw new ExtractionException(
                    "Extraction failed for %s at %s: %s"
                            .formatted(request.type(), request.url(), e.getMessage()),
                    e
            );

        } finally {
            // 5. Always cleanup session
            if (session != null) {
                try {
                    browserManager.closeSession(session.sessionId());
                    log.debug("Closed browser session: {}", session.sessionId());
                } catch (Exception e) {
                    log.warn("Failed to close browser session {}: {}",
                            session.sessionId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Gets the strategy for the given extraction type.
     *
     * @param type extraction type
     * @return strategy implementation
     * @throws ExtractionException if no strategy registered for type
     */
    private ExtractionStrategy getStrategy(ExtractionType type) {
        ExtractionStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new ExtractionException(
                    "No extraction strategy registered for type: %s. Available types: %s"
                            .formatted(type, strategies.keySet())
            );
        }
        return strategy;
    }

    /**
     * Gets all registered extraction types.
     * Useful for API documentation and validation.
     *
     * @return set of supported extraction types
     */
    public java.util.Set<ExtractionType> getSupportedTypes() {
        return strategies.keySet();
    }

    /**
     * Checks if a specific extraction type is supported.
     *
     * @param type extraction type to check
     * @return true if supported
     */
    public boolean isSupported(ExtractionType type) {
        return strategies.containsKey(type);
    }
}
