package com.browserapi.component.controller;

import com.browserapi.component.model.*;
import com.browserapi.component.service.ComponentExtractor;
import com.browserapi.component.service.ComponentExporter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    private final ComponentExporter componentExporter;

    public ComponentController(ComponentExtractor componentExtractor, ComponentExporter componentExporter) {
        this.componentExtractor = componentExtractor;
        this.componentExporter = componentExporter;
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

    @PostMapping("/extract/batch")
    @Operation(
            summary = "Extract multiple components in a single batch",
            description = """
                    Extracts multiple components from a single page using one browser session.
                    This is much more efficient than making multiple separate requests.

                    Features:
                    - Single browser session for all components
                    - Extract multiple matching elements per selector
                    - Individual options per component
                    - Global default options
                    - Detailed success/failure per component

                    Example request:
                    {
                      "url": "https://example.com",
                      "components": [
                        {
                          "name": "header",
                          "selector": "header",
                          "options": { "scopeCSS": true }
                        },
                        {
                          "name": "cards",
                          "selector": ".card",
                          "multiple": true
                        }
                      ],
                      "globalOptions": {
                        "scopeCSS": true,
                        "encapsulateJS": true,
                        "inlineAssets": true
                      }
                    }

                    Returns array of components with success status for each.
                    """
    )
    public ResponseEntity<?> extractBatch(@RequestBody BatchExtractionRequest request) {
        log.info("Batch extraction request: url={}, components={}",
                request.url(), request.components().size());

        try {
            BatchExtractionResult result = componentExtractor.extractBatch(request);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Batch extraction failed: url={}", request.url(), e);
            return ResponseEntity
                    .internalServerError()
                    .body(Map.of("error", "Batch extraction failed", "message", e.getMessage()));
        }
    }

    @PostMapping("/export")
    @Operation(
            summary = "Extract and export component in a specific format",
            description = """
                    Extracts a component and exports it in the specified format.

                    Supported formats:
                    - HTML: Standalone HTML file ready to open in browser
                    - REACT: React component with JSX and CSS files
                    - VUE: Vue Single File Component (.vue)
                    - WEB_COMPONENT: Web Component with Shadow DOM
                    - JSON: Raw JSON data (default API response)

                    Example request:
                    {
                      "url": "https://github.com",
                      "selector": ".card",
                      "format": "REACT",
                      "options": {
                        "scopeCSS": true,
                        "encapsulateJS": true,
                        "inlineAssets": true
                      }
                    }

                    Returns:
                    - files: Map of filename to content
                    - mainFile: The primary file to use
                    - usageInstructions: How to use the exported component
                    """
    )
    public ResponseEntity<?> exportComponent(@RequestBody ExportRequest request) {
        log.info("Component export request: url={}, selector={}, format={}",
                request.url(), request.selector(), request.format());

        try {
            ExtractionOptions options = request.options() != null
                    ? request.options()
                    : ExtractionOptions.defaults();

            // Extract complete component
            CompleteComponent component = componentExtractor.extract(
                    request.url(),
                    request.selector(),
                    options
            );

            // Export to requested format
            ExportFormat format = request.format() != null ? request.format() : ExportFormat.JSON;
            ComponentExport export = componentExporter.export(component, format);

            return ResponseEntity.ok(export);

        } catch (Exception e) {
            log.error("Component export failed: url={}, selector={}, format={}",
                    request.url(), request.selector(), request.format(), e);
            return ResponseEntity
                    .internalServerError()
                    .body(Map.of("error", "Component export failed", "message", e.getMessage()));
        }
    }

    @PostMapping("/export/download")
    @Operation(
            summary = "Export component and download main file",
            description = """
                    Exports a component in the specified format and returns the main file
                    as a downloadable attachment with appropriate Content-Type header.

                    For formats with multiple files (like REACT), only the main file is returned.
                    Use the /export endpoint to get all files.
                    """
    )
    public ResponseEntity<String> downloadExport(@RequestBody ExportRequest request) {
        log.info("Component export download request: url={}, selector={}, format={}",
                request.url(), request.selector(), request.format());

        try {
            ExtractionOptions options = request.options() != null
                    ? request.options()
                    : ExtractionOptions.defaults();

            // Extract complete component
            CompleteComponent component = componentExtractor.extract(
                    request.url(),
                    request.selector(),
                    options
            );

            // Export to requested format
            ExportFormat format = request.format() != null ? request.format() : ExportFormat.HTML;
            ComponentExport export = componentExporter.export(component, format);

            // Return main file as download
            String content = export.getMainContent();
            String filename = export.mainFile();
            String mimeType = format.getMimeType();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, mimeType)
                    .body(content);

        } catch (Exception e) {
            log.error("Component export download failed: url={}, selector={}, format={}",
                    request.url(), request.selector(), request.format(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    public record ExtractionRequest(
            String url,
            String selector,
            ExtractionOptions options
    ) {
    }

    public record ExportRequest(
            String url,
            String selector,
            ExportFormat format,
            ExtractionOptions options
    ) {
    }
}
