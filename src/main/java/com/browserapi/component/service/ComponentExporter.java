package com.browserapi.component.service;

import com.browserapi.component.model.CompleteComponent;
import com.browserapi.component.model.ComponentExport;
import com.browserapi.component.model.ExportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Exports components to various ready-to-use formats.
 */
@Service
public class ComponentExporter {

    private static final Logger log = LoggerFactory.getLogger(ComponentExporter.class);

    /**
     * Exports a component to the specified format.
     *
     * @param component the component to export
     * @param format the target format
     * @return export result with files and instructions
     */
    public ComponentExport export(CompleteComponent component, ExportFormat format) {
        log.info("Exporting component to format: {}", format);

        return switch (format) {
            case HTML -> exportAsHtml(component);
            case REACT -> exportAsReact(component);
            case VUE -> exportAsVue(component);
            case WEB_COMPONENT -> exportAsWebComponent(component);
            case JSON -> exportAsJson(component);
        };
    }

    /**
     * Exports as standalone HTML file.
     */
    private ComponentExport exportAsHtml(CompleteComponent component) {
        String html = String.format("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Extracted Component</title>
                  <style>
                    body {
                      font-family: system-ui, -apple-system, sans-serif;
                      margin: 0;
                      padding: 20px;
                    }
                  </style>
                </head>
                <body>
                  %s
                </body>
                </html>
                """, component.toHTML());

        String usage = String.format("""
                # Standalone HTML Component

                This is a complete, self-contained HTML file.

                ## How to Use:

                1. Save this file as 'component.html'
                2. Open it directly in your browser
                3. Or embed it in your website:

                ```html
                <iframe src="component.html" width="100%%" height="400"></iframe>
                ```

                The component includes:
                - Scoped CSS (namespace: %s)
                - Encapsulated JavaScript
                - Inlined assets (no external dependencies)
                """, component.namespace());

        return ComponentExport.single(ExportFormat.HTML, "component.html", html, usage);
    }

    /**
     * Exports as React component.
     */
    private ComponentExport exportAsReact(CompleteComponent component) {
        // Convert HTML to JSX
        String jsx = convertHtmlToJsx(component.html());

        // Extract component name from namespace
        String componentName = toPascalCase(component.namespace());

        // Create React component
        String reactComponent = String.format("""
                import React from 'react';
                import './%s.css';

                /**
                 * Extracted component from %s
                 * Selector: %s
                 * Extracted: %s
                 */
                export default function %s() {
                  return (
                    <div className="%s">
                %s
                    </div>
                  );
                }
                """,
                componentName,
                component.metadata().sourceUrl(),
                component.metadata().selector(),
                component.metadata().extractedAt(),
                componentName,
                component.namespace(),
                indentCode(jsx, 6)
        );

        // Create CSS file
        String cssFile = component.css() != null ? component.css() : "";

        // Create usage instructions
        String usage = String.format("""
                # React Component

                ## How to Use:

                1. Copy both files to your React project:
                   - %s.jsx
                   - %s.css

                2. Import and use the component:

                ```jsx
                import %s from './%s';

                function App() {
                  return (
                    <div>
                      <%s />
                    </div>
                  );
                }
                ```

                ## Features:
                - Scoped CSS with namespace '%s'
                - No external dependencies
                - All assets inlined
                """,
                componentName, componentName,
                componentName, componentName,
                componentName,
                component.namespace()
        );

        Map<String, String> files = new HashMap<>();
        files.put(componentName + ".jsx", reactComponent);
        files.put(componentName + ".css", cssFile);

        return ComponentExport.multiple(
                ExportFormat.REACT,
                files,
                componentName + ".jsx",
                usage
        );
    }

    /**
     * Exports as Vue Single File Component.
     */
    private ComponentExport exportAsVue(CompleteComponent component) {
        String componentName = toPascalCase(component.namespace());

        String vueComponent = String.format("""
                <template>
                  <div class="%s">
                %s
                  </div>
                </template>

                <script>
                export default {
                  name: '%s',
                  mounted() {
                %s
                  }
                }
                </script>

                <style scoped>
                %s
                </style>
                """,
                component.namespace(),
                indentCode(component.html() != null ? component.html() : "", 4),
                componentName,
                component.javascript() != null ? indentCode(component.javascript(), 4) : "    // No JavaScript",
                component.css() != null ? component.css() : ""
        );

        String usage = String.format("""
                # Vue Single File Component

                ## How to Use:

                1. Save as '%s.vue'

                2. Import and use in your Vue app:

                ```vue
                <template>
                  <%s />
                </template>

                <script>
                import %s from './%s.vue';

                export default {
                  components: {
                    %s
                  }
                }
                </script>
                ```

                ## Features:
                - Scoped styles (automatic in Vue SFC)
                - Component lifecycle hooks
                - All assets inlined
                """,
                componentName,
                componentName,
                componentName, componentName,
                componentName
        );

        return ComponentExport.single(
                ExportFormat.VUE,
                componentName + ".vue",
                vueComponent,
                usage
        );
    }

    /**
     * Exports as Web Component (Custom Element).
     */
    private ComponentExport exportAsWebComponent(CompleteComponent component) {
        String elementName = component.namespace();
        String className = toPascalCase(elementName);

        String webComponent = String.format("""
                /**
                 * Web Component: <%s>
                 * Extracted from: %s
                 */
                class %s extends HTMLElement {
                  constructor() {
                    super();

                    // Attach Shadow DOM
                    this.attachShadow({ mode: 'open' });

                    // Set content
                    this.shadowRoot.innerHTML = `
                      <style>
                %s
                      </style>

                %s
                    `;

                    // Initialize
                    this.init();
                  }

                  init() {
                %s
                  }

                  connectedCallback() {
                    console.log('<%s> connected to the DOM');
                  }

                  disconnectedCallback() {
                    console.log('<%s> disconnected from the DOM');
                  }
                }

                // Register the custom element
                customElements.define('%s', %s);

                // Usage: <%s></%s>
                """,
                elementName,
                component.metadata().sourceUrl(),
                className,
                indentCode(component.css() != null ? component.css() : "", 8),
                indentCode(component.html() != null ? component.html() : "", 6),
                component.javascript() != null ? indentCode(component.javascript(), 4) : "    // No JavaScript",
                elementName,
                elementName,
                elementName, className,
                elementName, elementName
        );

        String usage = String.format("""
                # Web Component (Custom Element)

                ## How to Use:

                1. Include the script in your HTML:

                ```html
                <script src="%s.js"></script>
                ```

                2. Use the custom element anywhere:

                ```html
                <%s></%s>
                ```

                ## Features:
                - Shadow DOM (complete style isolation)
                - Reusable across any framework
                - No build step required
                - Web standards compliant

                ## Browser Support:
                - Chrome/Edge: ✅
                - Firefox: ✅
                - Safari: ✅
                - IE11: ❌ (requires polyfill)
                """,
                elementName,
                elementName, elementName
        );

        return ComponentExport.single(
                ExportFormat.WEB_COMPONENT,
                elementName + ".js",
                webComponent,
                usage
        );
    }

    /**
     * Exports as JSON (default format).
     */
    private ComponentExport exportAsJson(CompleteComponent component) {
        // Component is already serializable to JSON
        String usage = """
                # JSON Export

                This is the raw component data in JSON format.

                ## Structure:
                - html: Component HTML
                - css: Scoped CSS
                - javascript: Encapsulated JavaScript
                - namespace: Unique CSS namespace
                - metadata: Source information
                - statistics: Extraction metrics

                ## Use this format to:
                - Build custom exporters
                - Store in database
                - Process with scripts
                - Integrate with custom tools
                """;

        return ComponentExport.single(
                ExportFormat.JSON,
                "component.json",
                "/* Component will be serialized as JSON */",
                usage
        );
    }

    /**
     * Converts HTML to JSX format.
     */
    private String convertHtmlToJsx(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        return html
                // Convert class to className
                .replaceAll("\\sclass=", " className=")
                // Convert for to htmlFor
                .replaceAll("\\sfor=", " htmlFor=")
                // Self-closing tags
                .replaceAll("<img([^>]*)>", "<img$1 />")
                .replaceAll("<br>", "<br />")
                .replaceAll("<hr>", "<hr />")
                .replaceAll("<input([^>]*)>", "<input$1 />");
    }

    /**
     * Converts kebab-case to PascalCase.
     */
    private String toPascalCase(String kebabCase) {
        String[] parts = kebabCase.split("-");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)))
                      .append(part.substring(1).toLowerCase());
            }
        }

        return result.toString();
    }

    /**
     * Indents code by specified number of spaces.
     */
    private String indentCode(String code, int spaces) {
        if (code == null || code.isBlank()) {
            return "";
        }

        String indent = " ".repeat(spaces);
        return code.lines()
                .map(line -> line.isBlank() ? line : indent + line)
                .reduce((a, b) -> a + "\n" + b)
                .orElse(code);
    }
}
