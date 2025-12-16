package com.browserapi.css.model;

/**
 * Represents a CSS rule with selector and declarations.
 * Includes metadata for deduplication and cascade ordering.
 */
public record CSSRule(
        String selector,
        String declarations,
        int specificity,
        String source,
        String mediaQuery
) {
    public CSSRule(String selector, String declarations) {
        this(selector, declarations, 0, "unknown", null);
    }

    public CSSRule(String selector, String declarations, String source) {
        this(selector, declarations, 0, source, null);
    }

    public String toCSS() {
        StringBuilder css = new StringBuilder();

        if (mediaQuery != null && !mediaQuery.isBlank()) {
            css.append(mediaQuery).append(" {\n  ");
        }

        css.append(selector).append(" {\n");

        String[] props = declarations.split(";");
        for (String prop : props) {
            prop = prop.trim();
            if (!prop.isEmpty()) {
                css.append("  ").append(prop).append(";\n");
            }
        }

        css.append("}");

        if (mediaQuery != null && !mediaQuery.isBlank()) {
            css.append("\n}");
        }

        return css.toString();
    }

    public String getKey() {
        return selector + "::" + declarations + "::" + (mediaQuery != null ? mediaQuery : "");
    }
}
