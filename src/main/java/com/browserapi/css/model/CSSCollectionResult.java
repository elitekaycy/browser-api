package com.browserapi.css.model;

import java.util.List;
import java.util.Map;

/**
 * Result of CSS collection operation.
 * Contains all CSS rules, variables, and metadata.
 */
public record CSSCollectionResult(
        List<CSSRule> rules,
        Map<String, String> variables,
        int totalRules,
        int deduplicatedRules,
        List<String> externalStylesheets
) {
    public String toCSS() {
        StringBuilder css = new StringBuilder();

        if (!variables.isEmpty()) {
            css.append("/* CSS Variables */\n");
            css.append(":root {\n");
            variables.forEach((key, value) -> {
                css.append("  ").append(key).append(": ").append(value).append(";\n");
            });
            css.append("}\n\n");
        }

        if (!rules.isEmpty()) {
            css.append("/* Component Styles */\n");
            for (CSSRule rule : rules) {
                css.append(rule.toCSS()).append("\n\n");
            }
        }

        return css.toString().trim();
    }
}
