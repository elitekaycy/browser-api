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

import java.util.*;

/**
 * Extraction strategy for CSS styles.
 * Extracts computed styles from elements and returns as CSS text or JSON.
 * <p>
 * Options:
 * <ul>
 *   <li>format (string, default: "css") - Output format: "css" or "json"</li>
 *   <li>properties (list, default: common) - Specific CSS properties to extract</li>
 *   <li>multiple (boolean, default: false) - Extract all matching elements</li>
 * </ul>
 */
@Component
public class CSSExtractor implements ExtractionStrategy {

    private static final Logger log = LoggerFactory.getLogger(CSSExtractor.class);

    private static final List<String> COMMON_CSS_PROPERTIES = List.of(
            "color", "background-color", "font-size", "font-family", "font-weight",
            "margin", "padding", "border", "width", "height", "display", "position"
    );

    @Override
    public ExtractionType getType() {
        return ExtractionType.CSS;
    }

    @Override
    public ExtractionResponse extract(ExtractionRequest request, PageSession session) {
        long startTime = System.currentTimeMillis();

        log.debug("Extracting CSS: selector={}, url={}",
                request.selector(), request.url());

        String format = request.getOption("format", "css");
        boolean multiple = request.getOption("multiple", false);
        List<String> properties = request.getOption("properties", null);

        try {
            Locator locator = session.page().locator(request.selector());
            int elementCount = locator.count();

            log.debug("Found {} element(s) for selector: {}", elementCount, request.selector());

            if (elementCount == 0) {
                throw new ExtractionException(
                        "No elements found for selector '%s' on page %s"
                                .formatted(request.selector(), request.url())
                );
            }

            String cssData;
            if (multiple) {
                cssData = extractMultiple(locator, elementCount, properties, format);
            } else {
                cssData = extractSingle(locator, properties, format);
            }

            long extractionTime = System.currentTimeMillis() - startTime;
            Map<String, Object> metadata = buildMetadata(
                    elementCount,
                    format,
                    multiple,
                    properties != null ? properties.size() : 0,
                    cssData.length()
            );

            log.info("CSS extraction completed: selector={}, elements={}, format={}, size={}, time={}ms",
                    request.selector(), elementCount, format, cssData.length(), extractionTime);

            return new ExtractionResponse(
                    cssData,
                    ExtractionType.CSS,
                    request.selector(),
                    extractionTime,
                    metadata
            );

        } catch (PlaywrightException e) {
            log.error("Playwright error during CSS extraction: selector={}, error={}",
                    request.selector(), e.getMessage());

            if (e.getMessage().contains("Invalid selector")) {
                throw new ExtractionException(
                        "Invalid CSS selector: " + request.selector(),
                        e
                );
            }

            throw new ExtractionException(
                    "Failed to extract CSS for selector '%s': %s"
                            .formatted(request.selector(), e.getMessage()),
                    e
            );

        } catch (ExtractionException e) {
            throw e;

        } catch (Exception e) {
            log.error("Unexpected error during CSS extraction: selector={}", request.selector(), e);
            throw new ExtractionException(
                    "Unexpected error during CSS extraction: " + e.getMessage(),
                    e
            );
        }
    }

    private String extractSingle(Locator locator, List<String> properties, String format) {
        Map<String, String> styles = getComputedStyles(locator.first(), properties);
        return formatStyles(styles, format);
    }

    private String extractMultiple(Locator locator, int count, List<String> properties, String format) {
        if ("json".equalsIgnoreCase(format)) {
            List<Map<String, String>> allStyles = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Map<String, String> styles = getComputedStyles(locator.nth(i), properties);
                allStyles.add(styles);
            }
            return formatMultipleAsJson(allStyles);
        } else {
            StringBuilder combined = new StringBuilder();
            for (int i = 0; i < count; i++) {
                Map<String, String> styles = getComputedStyles(locator.nth(i), properties);
                combined.append(formatStyles(styles, format));
                if (i < count - 1) {
                    combined.append("\n\n");
                }
            }
            return combined.toString();
        }
    }

    private Map<String, String> getComputedStyles(Locator locator, List<String> properties) {
        List<String> propsToExtract = properties != null ? properties : COMMON_CSS_PROPERTIES;

        String script = buildStyleExtractionScript(propsToExtract);
        Object result = locator.evaluate(script);

        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> styles = (Map<String, String>) result;
            return styles;
        }

        return new HashMap<>();
    }

    private String buildStyleExtractionScript(List<String> properties) {
        StringBuilder script = new StringBuilder();
        script.append("el => {\n");
        script.append("  const styles = window.getComputedStyle(el);\n");
        script.append("  return {\n");

        for (int i = 0; i < properties.size(); i++) {
            String prop = properties.get(i);
            script.append("    '").append(prop).append("': styles['").append(prop).append("']");
            if (i < properties.size() - 1) {
                script.append(",");
            }
            script.append("\n");
        }

        script.append("  };\n");
        script.append("}");

        return script.toString();
    }

    private String formatStyles(Map<String, String> styles, String format) {
        if ("json".equalsIgnoreCase(format)) {
            return formatAsJson(styles);
        } else {
            return formatAsCss(styles);
        }
    }

    private String formatAsCss(Map<String, String> styles) {
        StringBuilder css = new StringBuilder();
        styles.forEach((property, value) -> {
            css.append(property).append(": ").append(value).append(";\n");
        });
        return css.toString().trim();
    }

    private String formatAsJson(Map<String, String> styles) {
        StringBuilder json = new StringBuilder("{\n");
        List<Map.Entry<String, String>> entries = new ArrayList<>(styles.entrySet());

        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, String> entry = entries.get(i);
            json.append("  \"").append(entry.getKey()).append("\": \"")
                    .append(escapeJson(entry.getValue())).append("\"");
            if (i < entries.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("}");
        return json.toString();
    }

    private String formatMultipleAsJson(List<Map<String, String>> allStyles) {
        StringBuilder json = new StringBuilder("[\n");

        for (int i = 0; i < allStyles.size(); i++) {
            json.append("  ").append(formatAsJson(allStyles.get(i)).replace("\n", "\n  "));
            if (i < allStyles.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("]");
        return json.toString();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private Map<String, Object> buildMetadata(
            int elementCount,
            String format,
            boolean multiple,
            int propertyCount,
            int dataLength
    ) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("elementCount", elementCount);
        metadata.put("format", format);
        metadata.put("multiple", multiple);
        metadata.put("propertyCount", propertyCount > 0 ? propertyCount : COMMON_CSS_PROPERTIES.size());
        metadata.put("dataLength", dataLength);
        metadata.put("selectorMatched", true);
        return metadata;
    }
}
