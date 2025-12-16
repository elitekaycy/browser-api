package com.browserapi.js.controller;

import com.browserapi.browser.BrowserManager;
import com.browserapi.browser.PageSession;
import com.browserapi.browser.WaitStrategy;
import com.browserapi.js.model.JavaScriptCollectionResult;
import com.browserapi.js.service.JavaScriptCollector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API controller for JavaScript collection operations.
 * Provides endpoints to collect all JavaScript associated with components.
 */
@RestController
@RequestMapping("/api/v1/js/collect")
@Tag(name = "JavaScript Collection", description = "Collect JavaScript for components")
public class JavaScriptCollectionController {

    private static final Logger log = LoggerFactory.getLogger(JavaScriptCollectionController.class);

    private final JavaScriptCollector jsCollector;
    private final BrowserManager browserManager;

    public JavaScriptCollectionController(JavaScriptCollector jsCollector, BrowserManager browserManager) {
        this.jsCollector = jsCollector;
        this.browserManager = browserManager;
    }

    @GetMapping
    @Operation(
            summary = "Collect all JavaScript for a component",
            description = """
                    Collects all JavaScript associated with a component:
                    - Event listeners (addEventListener, best effort)
                    - Inline handlers (onclick, onload, etc.)
                    - Inline <script> tags
                    - External <script src=""> references

                    Example: GET /api/v1/js/collect?url=https://example.com&selector=.component

                    Note: Event listener extraction requires Chrome DevTools API or
                    pre-injected tracking code. May return empty array for listeners.
                    """
    )
    public ResponseEntity<?> collect(
            @RequestParam String url,
            @RequestParam String selector,
            @RequestParam(required = false) WaitStrategy wait
    ) {
        log.info("JavaScript collection request: url={}, selector={}", url, selector);

        PageSession session = null;
        try {
            session = browserManager.createSession(url, wait != null ? wait : WaitStrategy.LOAD);

            JavaScriptCollectionResult result = jsCollector.collect(session, selector);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("JavaScript collection failed: url={}, selector={}", url, selector, e);
            return ResponseEntity
                    .internalServerError()
                    .body(Map.of("error", "JavaScript collection failed", "message", e.getMessage()));

        } finally {
            if (session != null) {
                try {
                    browserManager.closeSession(session.sessionId());
                } catch (Exception e) {
                    log.warn("Failed to close session", e);
                }
            }
        }
    }
}
