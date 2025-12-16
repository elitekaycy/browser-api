package com.browserapi.css.controller;

import com.browserapi.browser.BrowserManager;
import com.browserapi.browser.PageSession;
import com.browserapi.browser.WaitStrategy;
import com.browserapi.css.model.CSSCollectionResult;
import com.browserapi.css.model.ScopedCSSResult;
import com.browserapi.css.service.CSSCollector;
import com.browserapi.css.service.CSSScoper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API controller for CSS collection operations.
 * Provides endpoint to collect all CSS affecting a component.
 */
@RestController
@RequestMapping("/api/v1/css/collect")
@Tag(name = "CSS Collection", description = "Collect all CSS for components")
public class CSSCollectionController {

    private static final Logger log = LoggerFactory.getLogger(CSSCollectionController.class);

    private final CSSCollector cssCollector;
    private final CSSScoper cssScoper;
    private final BrowserManager browserManager;

    public CSSCollectionController(CSSCollector cssCollector, CSSScoper cssScoper, BrowserManager browserManager) {
        this.cssCollector = cssCollector;
        this.cssScoper = cssScoper;
        this.browserManager = browserManager;
    }

    @GetMapping
    @Operation(
            summary = "Collect all CSS for a component",
            description = """
                    Collects all CSS rules, variables, and stylesheets affecting a component.
                    Includes inline styles, internal stylesheets, and references to external stylesheets.

                    Example: GET /api/v1/css/collect?url=https://example.com&selector=h1
                    """
    )
    public ResponseEntity<?> collect(
            @RequestParam String url,
            @RequestParam String selector,
            @RequestParam(required = false) WaitStrategy wait
    ) {
        log.info("CSS collection request: url={}, selector={}", url, selector);

        PageSession session = null;
        try {
            session = browserManager.createSession(url, wait != null ? wait : WaitStrategy.LOAD);

            CSSCollectionResult result = cssCollector.collect(session, selector);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("CSS collection failed: url={}, selector={}", url, selector, e);
            return ResponseEntity
                    .internalServerError()
                    .body(Map.of("error", "CSS collection failed", "message", e.getMessage()));

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

    @GetMapping("/css")
    @Operation(
            summary = "Collect CSS as formatted CSS text",
            description = "Same as /collect but returns formatted CSS text instead of JSON."
    )
    public ResponseEntity<String> collectAsCSS(
            @RequestParam String url,
            @RequestParam String selector,
            @RequestParam(required = false) WaitStrategy wait
    ) {
        log.info("CSS collection (as CSS) request: url={}, selector={}", url, selector);

        PageSession session = null;
        try {
            session = browserManager.createSession(url, wait != null ? wait : WaitStrategy.LOAD);

            CSSCollectionResult result = cssCollector.collect(session, selector);

            return ResponseEntity
                    .ok()
                    .header("Content-Type", "text/css")
                    .body(result.toCSS());

        } catch (Exception e) {
            log.error("CSS collection failed: url={}, selector={}", url, selector, e);
            return ResponseEntity
                    .internalServerError()
                    .body("/* CSS collection failed: " + e.getMessage() + " */");

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

    @GetMapping("/scope")
    @Operation(
            summary = "Collect and scope CSS with unique namespace",
            description = """
                    Collects CSS for a component and adds unique namespace to prevent conflicts.
                    Returns JSON with scoped CSS, namespace, and renamed keyframes.

                    Example: GET /api/v1/css/collect/scope?url=https://example.com&selector=h1
                    """
    )
    public ResponseEntity<?> scope(
            @RequestParam String url,
            @RequestParam String selector,
            @RequestParam(required = false) WaitStrategy wait,
            @RequestParam(required = false) String namespace
    ) {
        log.info("CSS scoping request: url={}, selector={}, namespace={}", url, selector, namespace);

        PageSession session = null;
        try {
            session = browserManager.createSession(url, wait != null ? wait : WaitStrategy.LOAD);

            CSSCollectionResult collected = cssCollector.collect(session, selector);
            ScopedCSSResult scoped = cssScoper.scope(collected, namespace);

            return ResponseEntity.ok(scoped);

        } catch (Exception e) {
            log.error("CSS scoping failed: url={}, selector={}", url, selector, e);
            return ResponseEntity
                    .internalServerError()
                    .body(Map.of("error", "CSS scoping failed", "message", e.getMessage()));

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

    @GetMapping("/scope/css")
    @Operation(
            summary = "Collect and scope CSS as formatted CSS text",
            description = "Same as /scope but returns formatted scoped CSS text instead of JSON."
    )
    public ResponseEntity<String> scopeAsCSS(
            @RequestParam String url,
            @RequestParam String selector,
            @RequestParam(required = false) WaitStrategy wait,
            @RequestParam(required = false) String namespace
    ) {
        log.info("CSS scoping (as CSS) request: url={}, selector={}, namespace={}", url, selector, namespace);

        PageSession session = null;
        try {
            session = browserManager.createSession(url, wait != null ? wait : WaitStrategy.LOAD);

            CSSCollectionResult collected = cssCollector.collect(session, selector);
            ScopedCSSResult scoped = cssScoper.scope(collected, namespace);

            return ResponseEntity
                    .ok()
                    .header("Content-Type", "text/css")
                    .body(scoped.scopedCSS());

        } catch (Exception e) {
            log.error("CSS scoping failed: url={}, selector={}", url, selector, e);
            return ResponseEntity
                    .internalServerError()
                    .body("/* CSS scoping failed: " + e.getMessage() + " */");

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
