package com.browserapi.css.model;

import java.util.List;
import java.util.Map;

/**
 * Result of CSS scoping operation.
 * Contains scoped CSS with unique namespace to prevent conflicts.
 */
public record ScopedCSSResult(
        String namespace,
        String scopedCSS,
        List<CSSRule> scopedRules,
        Map<String, String> renamedKeyframes,
        int originalRules,
        int scopedRulesCount
) {
    public String toInlinedHTML(String componentHTML) {
        return String.format("""
                <div class="%s">
                  <style>
                    %s
                  </style>
                  %s
                </div>
                """, namespace, scopedCSS, componentHTML);
    }
}
