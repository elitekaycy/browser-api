package com.browserapi.component.model;

/**
 * A complete, self-contained component ready for use.
 * Includes HTML, scoped CSS, encapsulated JavaScript, and inlined assets.
 */
public record CompleteComponent(
        String html,
        String css,
        String javascript,
        String namespace,
        ComponentMetadata metadata,
        ExtractionStatistics statistics
) {
    /**
     * Returns the complete component as a single HTML string ready to use.
     * Combines HTML, CSS, and JavaScript into one drop-in snippet.
     */
    public String toHTML() {
        StringBuilder result = new StringBuilder();

        // Wrapper div with namespace
        result.append("<div class=\"").append(namespace).append("\">\n");

        // Scoped CSS
        if (css != null && !css.isBlank()) {
            result.append("  <style>\n");
            result.append(indentCode(css, 4));
            result.append("\n  </style>\n\n");
        }

        // HTML content
        if (html != null && !html.isBlank()) {
            result.append(indentCode(html, 2));
            result.append("\n\n");
        }

        // Encapsulated JavaScript
        if (javascript != null && !javascript.isBlank()) {
            result.append("  <script>\n");
            result.append(indentCode(javascript, 4));
            result.append("\n  </script>\n");
        }

        result.append("</div>");

        return result.toString();
    }

    /**
     * Returns usage instructions for this component.
     */
    public String getUsageInstructions() {
        return String.format("""
                # Usage Instructions

                This is a complete, self-contained component extracted from:
                - Source: %s
                - Selector: %s
                - Extracted: %s

                ## How to Use:

                Simply copy the HTML below and paste it anywhere in your website.
                It will work immediately with no dependencies!

                ✅ All CSS is scoped with namespace '%s' (no conflicts)
                ✅ All JavaScript is encapsulated (no conflicts)
                ✅ All assets are inlined as Base64 (no broken links)

                ## Statistics:
                - HTML Elements: %d
                - CSS Rules: %d
                - JavaScript Listeners: %d
                - Inlined Assets: %d
                - Original Size: %d bytes
                - Final Size: %d bytes
                - Size Ratio: %.1f%%
                """,
                metadata.sourceUrl(),
                metadata.selector(),
                metadata.extractedAt(),
                namespace,
                statistics.htmlElements(),
                statistics.cssRules(),
                statistics.jsListeners(),
                statistics.inlinedAssets(),
                statistics.originalSize(),
                statistics.finalSize(),
                statistics.compressionRatio()
        );
    }

    private String indentCode(String code, int spaces) {
        String indent = " ".repeat(spaces);
        return code.lines()
                .map(line -> line.isBlank() ? line : indent + line)
                .reduce((a, b) -> a + "\n" + b)
                .orElse(code);
    }
}
