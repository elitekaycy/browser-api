package com.browserapi.component.controller;

import com.browserapi.component.model.CompleteComponent;
import com.browserapi.component.model.ExtractionOptions;
import com.browserapi.component.service.ComponentExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API controller for complete component extraction.
 */
@RestController
@RequestMapping("/api/v1/components")
@Tag(name = "Complete Component Extraction", description = "Extract complete, self-contained components")
public class ComponentController {

    private static final Logger log = LoggerFactory.getLogger(ComponentController.class);

    private final ComponentExtractor componentExtractor;

    public ComponentController(ComponentExtractor componentExtractor) {
        this.componentExtractor = componentExtractor;
    }

    @PostMapping("/extract")
    @Operation(
            summary = "Extract a complete, self-contained component",
            description = """
                    Extracts a complete component that is ready to drop into any website:

                    1. Extracts HTML for the selector
                    2. Collects and scopes CSS (prevents conflicts)
                    3. Collects and encapsulates JavaScript (prevents conflicts)
                    4. Detects and inlines assets as Base64 (no broken links)

                    The result is a fully portable component that works anywhere!

                    Example request:
                    {
                      "url": "https://github.com",
                      "selector": ".card",
                      "options": {
                        "scopeCSS": true,
                        "encapsulateJS": true,
                        "inlineAssets": true
                      }
                    }

                    Example response:
                    {
                      "html": "<div>...</div>",
                      "css": ".component-abc123 .card { ... }",
                      "javascript": "((function(rootElement) { ... })())",
                      "namespace": "component-abc123",
                      "metadata": { ... },
                      "statistics": { ... }
                    }

                    Use the toHTML() method result or call /extract/html endpoint
                    to get the complete ready-to-use HTML snippet.
                    """
    )
    public ResponseEntity<?> extractComponent(@RequestBody ExtractionRequest request) {
        log.info("Complete component extraction request: url={}, selector={}",
                request.url(), request.selector());

        try {
            ExtractionOptions options = request.options() != null
                    ? request.options()
                    : ExtractionOptions.defaults();

            CompleteComponent component = componentExtractor.extract(
                    request.url(),
                    request.selector(),
                    options
            );

            return ResponseEntity.ok(component);

        } catch (Exception e) {
            log.error("Complete component extraction failed: url={}, selector={}",
                    request.url(), request.selector(), e);
            return ResponseEntity
                    .internalServerError()
                    .body(Map.of("error", "Component extraction failed", "message", e.getMessage()));
        }
    }

    @PostMapping("/extract/html")
    @Operation(
            summary = "Extract complete component as ready-to-use HTML",
            description = """
                    Same as /extract but returns the complete component as a single
                    HTML snippet ready to copy-paste into any website.

                    The returned HTML includes:
                    - Scoped CSS in <style> tag
                    - HTML content with inlined assets
                    - Encapsulated JavaScript in <script> tag
                    - Everything wrapped in a namespaced div

                    Just copy this HTML and paste it anywhere - it will work!
                    """
    )
    public ResponseEntity<String> extractComponentHTML(@RequestBody ExtractionRequest request) {
        log.info("Complete component HTML extraction request: url={}, selector={}",
                request.url(), request.selector());

        try {
            ExtractionOptions options = request.options() != null
                    ? request.options()
                    : ExtractionOptions.defaults();

            CompleteComponent component = componentExtractor.extract(
                    request.url(),
                    request.selector(),
                    options
            );

            String html = component.toHTML();

            return ResponseEntity
                    .ok()
                    .header("Content-Type", "text/html")
                    .body(html);

        } catch (Exception e) {
            log.error("Complete component HTML extraction failed: url={}, selector={}",
                    request.url(), request.selector(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/extract/usage")
    @Operation(
            summary = "Extract component with usage instructions",
            description = "Returns the component along with detailed usage instructions"
    )
    public ResponseEntity<?> extractComponentWithUsage(@RequestBody ExtractionRequest request) {
        log.info("Component extraction with usage request: url={}, selector={}",
                request.url(), request.selector());

        try {
            ExtractionOptions options = request.options() != null
                    ? request.options()
                    : ExtractionOptions.defaults();

            CompleteComponent component = componentExtractor.extract(
                    request.url(),
                    request.selector(),
                    options
            );

            return ResponseEntity.ok(Map.of(
                    "component", component,
                    "html", component.toHTML(),
                    "usage", component.getUsageInstructions()
            ));

        } catch (Exception e) {
            log.error("Component extraction with usage failed: url={}, selector={}",
                    request.url(), request.selector(), e);
            return ResponseEntity
                    .internalServerError()
                    .body(Map.of("error", "Component extraction failed", "message", e.getMessage()));
        }
    }

    public record ExtractionRequest(
            String url,
            String selector,
            ExtractionOptions options
    ) {
    }
}
