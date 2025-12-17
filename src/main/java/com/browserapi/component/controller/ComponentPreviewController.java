package com.browserapi.component.controller;

import com.browserapi.component.model.CompleteComponent;
import com.browserapi.component.model.ExtractionOptions;
import com.browserapi.component.service.ComponentExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for component preview interface.
 * Provides visual preview with multiple viewing contexts.
 */
@RestController
@RequestMapping("/api/v1/components")
@Tag(name = "Component Preview", description = "Visual preview interface for components")
public class ComponentPreviewController {

    private static final Logger log = LoggerFactory.getLogger(ComponentPreviewController.class);

    private final ComponentExtractor componentExtractor;

    public ComponentPreviewController(ComponentExtractor componentExtractor) {
        this.componentExtractor = componentExtractor;
    }

    @GetMapping("/preview")
    @Operation(
            summary = "Preview a component in visual interface",
            description = """
                    Extracts a component and displays it in a visual preview interface.

                    Features:
                    - Live component rendering
                    - Light/dark mode toggle
                    - Desktop/mobile/tablet views
                    - Metadata and statistics display
                    - Copy HTML and download buttons
                    - Source code viewer

                    Example: GET /api/v1/components/preview?url=https://example.com&selector=h1
                    """
    )
    public ResponseEntity<String> previewComponent(
            @Parameter(description = "Source URL to extract from", required = true)
            @RequestParam String url,

            @Parameter(description = "CSS selector for the component", required = true)
            @RequestParam String selector,

            @Parameter(description = "Scope CSS to prevent conflicts", required = false)
            @RequestParam(defaultValue = "true") boolean scopeCSS,

            @Parameter(description = "Encapsulate JavaScript", required = false)
            @RequestParam(defaultValue = "false") boolean encapsulateJS,

            @Parameter(description = "Inline assets as base64", required = false)
            @RequestParam(defaultValue = "true") boolean inlineAssets
    ) {
        log.info("Component preview request: url={}, selector={}", url, selector);

        try {
            ExtractionOptions options = new ExtractionOptions(
                    scopeCSS,
                    encapsulateJS,
                    inlineAssets,
                    null, null, null, null, null
            );

            CompleteComponent component = componentExtractor.extract(url, selector, options);

            String previewHtml = generatePreviewPage(component, url, selector);

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(previewHtml);

        } catch (Exception e) {
            log.error("Component preview failed: url={}, selector={}", url, selector, e);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(generateErrorPage(url, selector, e.getMessage()));
        }
    }

    /**
     * Generates the preview HTML page with the component and controls.
     */
    private String generatePreviewPage(CompleteComponent component, String url, String selector) {
        String componentHtml = component.toHTML();
        String escapedHtml = escapeHtml(componentHtml);

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Component Preview - %s</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            background: #f5f5f5;
        }

        .header {
            background: white;
            border-bottom: 1px solid #e0e0e0;
            padding: 20px;
            position: sticky;
            top: 0;
            z-index: 1000;
            box-shadow: 0 2px 4px rgba(0,0,0,0.05);
        }

        .header h1 {
            font-size: 24px;
            color: #333;
            margin-bottom: 10px;
        }

        .header-info {
            display: flex;
            gap: 20px;
            flex-wrap: wrap;
            font-size: 14px;
            color: #666;
        }

        .header-info span {
            display: flex;
            align-items: center;
            gap: 5px;
        }

        .header-info strong {
            color: #333;
        }

        .controls {
            background: white;
            border-bottom: 1px solid #e0e0e0;
            padding: 15px 20px;
            display: flex;
            gap: 15px;
            flex-wrap: wrap;
            align-items: center;
        }

        .control-group {
            display: flex;
            gap: 10px;
            align-items: center;
        }

        .control-group label {
            font-size: 14px;
            color: #666;
            font-weight: 500;
        }

        button, select {
            padding: 8px 16px;
            border: 1px solid #ddd;
            border-radius: 4px;
            background: white;
            cursor: pointer;
            font-size: 14px;
            transition: all 0.2s;
        }

        button:hover, select:hover {
            background: #f5f5f5;
            border-color: #999;
        }

        button.active {
            background: #007bff;
            color: white;
            border-color: #007bff;
        }

        .preview-container {
            max-width: 1400px;
            margin: 20px auto;
            padding: 0 20px;
        }

        .preview-frame {
            background: white;
            border: 1px solid #e0e0e0;
            border-radius: 8px;
            padding: 40px;
            margin-bottom: 20px;
            min-height: 400px;
            transition: all 0.3s;
        }

        .preview-frame.dark {
            background: #1e1e1e;
            border-color: #333;
        }

        .preview-frame.mobile {
            max-width: 375px;
            margin: 0 auto 20px;
        }

        .preview-frame.tablet {
            max-width: 768px;
            margin: 0 auto 20px;
        }

        .metadata {
            background: white;
            border: 1px solid #e0e0e0;
            border-radius: 8px;
            padding: 20px;
            margin-bottom: 20px;
        }

        .metadata h3 {
            font-size: 18px;
            margin-bottom: 15px;
            color: #333;
        }

        .metadata-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 15px;
        }

        .metadata-item {
            padding: 10px;
            background: #f9f9f9;
            border-radius: 4px;
        }

        .metadata-item label {
            display: block;
            font-size: 12px;
            color: #666;
            margin-bottom: 5px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .metadata-item value {
            display: block;
            font-size: 14px;
            color: #333;
            font-weight: 500;
        }

        .code-viewer {
            background: white;
            border: 1px solid #e0e0e0;
            border-radius: 8px;
            padding: 20px;
            margin-bottom: 20px;
        }

        .code-viewer h3 {
            font-size: 18px;
            margin-bottom: 15px;
            color: #333;
        }

        .code-viewer pre {
            background: #f5f5f5;
            border: 1px solid #e0e0e0;
            border-radius: 4px;
            padding: 15px;
            overflow-x: auto;
            font-size: 13px;
            line-height: 1.5;
        }

        .code-viewer code {
            font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
            color: #333;
        }

        .actions {
            display: flex;
            gap: 10px;
            margin-top: 15px;
        }

        .btn-primary {
            background: #007bff;
            color: white;
            border-color: #007bff;
        }

        .btn-primary:hover {
            background: #0056b3;
            border-color: #0056b3;
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>🎨 Component Preview</h1>
        <div class="header-info">
            <span><strong>Source:</strong> %s</span>
            <span><strong>Selector:</strong> <code>%s</code></span>
            <span><strong>Namespace:</strong> <code>%s</code></span>
        </div>
    </div>

    <div class="controls">
        <div class="control-group">
            <label>Theme:</label>
            <button id="theme-light" class="active" onclick="setTheme('light')">☀️ Light</button>
            <button id="theme-dark" onclick="setTheme('dark')">🌙 Dark</button>
        </div>

        <div class="control-group">
            <label>Device:</label>
            <button id="device-desktop" class="active" onclick="setDevice('desktop')">🖥️ Desktop</button>
            <button id="device-tablet" onclick="setDevice('tablet')">📱 Tablet</button>
            <button id="device-mobile" onclick="setDevice('mobile')">📱 Mobile</button>
        </div>

        <div class="control-group">
            <button onclick="copyHTML()" class="btn-primary">📋 Copy HTML</button>
            <button onclick="downloadHTML()" class="btn-primary">⬇️ Download</button>
        </div>
    </div>

    <div class="preview-container">
        <div class="preview-frame" id="preview-frame">
            %s
        </div>

        <div class="metadata">
            <h3>📊 Component Statistics</h3>
            <div class="metadata-grid">
                <div class="metadata-item">
                    <label>HTML Elements</label>
                    <value>%d</value>
                </div>
                <div class="metadata-item">
                    <label>CSS Rules</label>
                    <value>%d</value>
                </div>
                <div class="metadata-item">
                    <label>JS Event Listeners</label>
                    <value>%d</value>
                </div>
                <div class="metadata-item">
                    <label>Assets Inlined</label>
                    <value>%d</value>
                </div>
                <div class="metadata-item">
                    <label>Total Size</label>
                    <value>%d KB</value>
                </div>
                <div class="metadata-item">
                    <label>Extracted At</label>
                    <value>%s</value>
                </div>
            </div>
        </div>

        <div class="code-viewer">
            <h3>💻 Source Code</h3>
            <pre><code>%s</code></pre>
            <div class="actions">
                <button onclick="copyHTML()" class="btn-primary">Copy to Clipboard</button>
            </div>
        </div>
    </div>

    <script>
        const componentHTML = `%s`;

        function setTheme(theme) {
            const frame = document.getElementById('preview-frame');
            document.querySelectorAll('[id^="theme-"]').forEach(btn => btn.classList.remove('active'));
            document.getElementById('theme-' + theme).classList.add('active');

            if (theme === 'dark') {
                frame.classList.add('dark');
            } else {
                frame.classList.remove('dark');
            }
        }

        function setDevice(device) {
            const frame = document.getElementById('preview-frame');
            document.querySelectorAll('[id^="device-"]').forEach(btn => btn.classList.remove('active'));
            document.getElementById('device-' + device).classList.add('active');

            frame.classList.remove('mobile', 'tablet');
            if (device !== 'desktop') {
                frame.classList.add(device);
            }
        }

        function copyHTML() {
            navigator.clipboard.writeText(componentHTML).then(() => {
                alert('✅ HTML copied to clipboard!');
            }).catch(err => {
                alert('❌ Failed to copy: ' + err);
            });
        }

        function downloadHTML() {
            const blob = new Blob([componentHTML], { type: 'text/html' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'component.html';
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
        }
    </script>
</body>
</html>
                """.formatted(
                selector,
                url,
                selector,
                component.namespace(),
                componentHtml,
                component.statistics().htmlElements(),
                component.statistics().cssRules(),
                component.statistics().jsListeners(),
                component.statistics().inlinedAssets(),
                component.statistics().finalSize() / 1024,
                component.metadata().extractedAt(),
                escapedHtml,
                escapeJavaScript(componentHtml)
        );
    }

    /**
     * Generates an error page when preview fails.
     */
    private String generateErrorPage(String url, String selector, String error) {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Preview Error</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            background: #f5f5f5;
            margin: 0;
        }
        .error-container {
            background: white;
            border: 1px solid #e0e0e0;
            border-radius: 8px;
            padding: 40px;
            max-width: 600px;
            text-align: center;
        }
        h1 {
            color: #d32f2f;
            margin-bottom: 20px;
        }
        .error-details {
            background: #fff3cd;
            border: 1px solid #ffc107;
            border-radius: 4px;
            padding: 15px;
            margin: 20px 0;
            text-align: left;
        }
        .error-details pre {
            margin: 10px 0 0 0;
            white-space: pre-wrap;
            word-wrap: break-word;
        }
    </style>
</head>
<body>
    <div class="error-container">
        <h1>❌ Preview Failed</h1>
        <p>Could not extract component from the specified URL and selector.</p>
        <div class="error-details">
            <strong>URL:</strong> %s<br>
            <strong>Selector:</strong> %s
            <pre>%s</pre>
        </div>
        <p><a href="javascript:history.back()">← Go Back</a></p>
    </div>
</body>
</html>
                """.formatted(url, selector, escapeHtml(error));
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    private String escapeJavaScript(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("$", "\\$")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
