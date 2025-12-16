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
 * Extraction strategy for JSON data.
 * Converts DOM elements to structured JSON using schema mapping or simple extraction.
 * <p>
 * Options:
 * <ul>
 *   <li>schema (map, default: null) - Schema defining what to extract</li>
 *   <li>attributes (boolean, default: false) - Extract all element attributes</li>
 *   <li>includeText (boolean, default: true) - Include text content</li>
 *   <li>multiple (boolean, default: false) - Extract all matching elements as array</li>
 * </ul>
 * <p>
 * Schema syntax:
 * <ul>
 *   <li>"field": "selector" - Extract text from nested selector</li>
 *   <li>"field": "selector@attr" - Extract attribute from nested selector</li>
 *   <li>"field": "@attr" - Extract attribute from current element</li>
 * </ul>
 */
@Component
public class JSONExtractor implements ExtractionStrategy {

    private static final Logger log = LoggerFactory.getLogger(JSONExtractor.class);

    @Override
    public ExtractionType getType() {
        return ExtractionType.JSON;
    }

    @Override
    public ExtractionResponse extract(ExtractionRequest request, PageSession session) {
        long startTime = System.currentTimeMillis();

        log.debug("Extracting JSON: selector={}, url={}",
                request.selector(), request.url());

        Map<String, Object> schema = request.getOption("schema", null);
        boolean extractAttributes = request.getOption("attributes", false);
        boolean includeText = request.getOption("includeText", true);
        boolean multiple = request.getOption("multiple", false);

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

            String jsonData;
            if (multiple) {
                jsonData = extractMultiple(locator, elementCount, schema, extractAttributes, includeText);
            } else {
                jsonData = extractSingle(locator.first(), schema, extractAttributes, includeText);
            }

            long extractionTime = System.currentTimeMillis() - startTime;
            Map<String, Object> metadata = buildMetadata(
                    elementCount,
                    schema != null,
                    extractAttributes,
                    multiple,
                    jsonData.length()
            );

            log.info("JSON extraction completed: selector={}, elements={}, size={}, time={}ms",
                    request.selector(), elementCount, jsonData.length(), extractionTime);

            return new ExtractionResponse(
                    jsonData,
                    ExtractionType.JSON,
                    request.selector(),
                    extractionTime,
                    metadata
            );

        } catch (PlaywrightException e) {
            log.error("Playwright error during JSON extraction: selector={}, error={}",
                    request.selector(), e.getMessage());

            if (e.getMessage().contains("Invalid selector")) {
                throw new ExtractionException(
                        "Invalid CSS selector: " + request.selector(),
                        e
                );
            }

            throw new ExtractionException(
                    "Failed to extract JSON for selector '%s': %s"
                            .formatted(request.selector(), e.getMessage()),
                    e
            );

        } catch (ExtractionException e) {
            throw e;

        } catch (Exception e) {
            log.error("Unexpected error during JSON extraction: selector={}", request.selector(), e);
            throw new ExtractionException(
                    "Unexpected error during JSON extraction: " + e.getMessage(),
                    e
            );
        }
    }

    private String extractSingle(Locator locator, Map<String, Object> schema,
                                  boolean extractAttributes, boolean includeText) {
        Map<String, Object> data = extractElementData(locator, schema, extractAttributes, includeText);
        return formatAsJson(data);
    }

    private String extractMultiple(Locator locator, int count, Map<String, Object> schema,
                                    boolean extractAttributes, boolean includeText) {
        List<Map<String, Object>> allData = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Map<String, Object> data = extractElementData(locator.nth(i), schema, extractAttributes, includeText);
            allData.add(data);
        }

        return formatAsJsonArray(allData);
    }

    private Map<String, Object> extractElementData(Locator locator, Map<String, Object> schema,
                                                     boolean extractAttributes, boolean includeText) {
        Map<String, Object> data = new LinkedHashMap<>();

        if (schema != null && !schema.isEmpty()) {
            data.putAll(extractWithSchema(locator, schema));
        } else {
            if (includeText) {
                String text = locator.textContent();
                if (text != null && !text.isBlank()) {
                    data.put("text", text.trim());
                }
            }

            if (extractAttributes) {
                Map<String, String> attrs = extractAllAttributes(locator);
                if (!attrs.isEmpty()) {
                    data.put("attributes", attrs);
                }
            }
        }

        return data;
    }

    private Map<String, Object> extractWithSchema(Locator baseLocator, Map<String, Object> schema) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String) {
                String selector = (String) value;
                Object extractedValue = extractSchemaField(baseLocator, selector);
                if (extractedValue != null) {
                    result.put(field, extractedValue);
                }
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedSchema = (Map<String, Object>) value;
                Map<String, Object> nestedData = extractWithSchema(baseLocator, nestedSchema);
                if (!nestedData.isEmpty()) {
                    result.put(field, nestedData);
                }
            }
        }

        return result;
    }

    private Object extractSchemaField(Locator baseLocator, String selector) {
        if (selector.startsWith("@")) {
            String attr = selector.substring(1);
            return baseLocator.getAttribute(attr);
        }

        if (selector.contains("@")) {
            String[] parts = selector.split("@", 2);
            String elementSelector = parts[0].trim();
            String attr = parts[1].trim();

            try {
                Locator targetLocator = baseLocator.locator(elementSelector);
                if (targetLocator.count() > 0) {
                    return targetLocator.first().getAttribute(attr);
                }
            } catch (Exception e) {
                log.debug("Failed to extract attribute {} from selector {}: {}",
                        attr, elementSelector, e.getMessage());
            }
            return null;
        }

        try {
            Locator targetLocator = baseLocator.locator(selector);
            if (targetLocator.count() > 0) {
                String text = targetLocator.first().textContent();
                return text != null ? text.trim() : null;
            }
        } catch (Exception e) {
            log.debug("Failed to extract text from selector {}: {}", selector, e.getMessage());
        }

        return null;
    }

    private Map<String, String> extractAllAttributes(Locator locator) {
        String script = """
                el => {
                    const attrs = {};
                    for (const attr of el.attributes) {
                        attrs[attr.name] = attr.value;
                    }
                    return attrs;
                }
                """;

        Object result = locator.evaluate(script);

        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> attrs = (Map<String, String>) result;
            return attrs;
        }

        return new HashMap<>();
    }

    private String formatAsJson(Map<String, Object> data) {
        return formatJsonObject(data, 0);
    }

    private String formatAsJsonArray(List<Map<String, Object>> dataList) {
        StringBuilder json = new StringBuilder("[\n");

        for (int i = 0; i < dataList.size(); i++) {
            String itemJson = formatJsonObject(dataList.get(i), 1);
            json.append("  ").append(itemJson.replace("\n", "\n  "));
            if (i < dataList.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("]");
        return json.toString();
    }

    private String formatJsonObject(Map<String, Object> obj, int indentLevel) {
        if (obj.isEmpty()) {
            return "{}";
        }

        StringBuilder json = new StringBuilder("{\n");
        List<Map.Entry<String, Object>> entries = new ArrayList<>(obj.entrySet());

        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Object> entry = entries.get(i);
            String indent = "  ".repeat(indentLevel + 1);

            json.append(indent).append("\"").append(entry.getKey()).append("\": ");

            Object value = entry.getValue();
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                String nestedJson = formatJsonObject(nestedMap, indentLevel + 1);
                json.append(nestedJson.replace("\n", "\n" + indent));
            } else if (value instanceof String) {
                json.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value == null) {
                json.append("null");
            } else {
                json.append("\"").append(escapeJson(value.toString())).append("\"");
            }

            if (i < entries.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("  ".repeat(indentLevel)).append("}");
        return json.toString();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private Map<String, Object> buildMetadata(
            int elementCount,
            boolean hasSchema,
            boolean extractedAttributes,
            boolean multiple,
            int dataLength
    ) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("elementCount", elementCount);
        metadata.put("hasSchema", hasSchema);
        metadata.put("extractedAttributes", extractedAttributes);
        metadata.put("multiple", multiple);
        metadata.put("dataLength", dataLength);
        metadata.put("selectorMatched", true);
        return metadata;
    }
}
