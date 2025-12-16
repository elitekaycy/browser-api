package com.browserapi.css.service;

import com.browserapi.browser.PageSession;
import com.browserapi.css.model.CSSCollectionResult;
import com.browserapi.css.model.CSSRule;
import com.microsoft.playwright.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Collects all CSS affecting a component.
 * Includes inline styles, stylesheet rules, CSS variables, and external stylesheets.
 */
@Service
public class CSSCollector {

    private static final Logger log = LoggerFactory.getLogger(CSSCollector.class);

    /**
     * Collects all CSS for elements matching the selector.
     *
     * @param session browser session
     * @param selector CSS selector
     * @return collection result with rules and variables
     */
    public CSSCollectionResult collect(PageSession session, String selector) {
        log.debug("Collecting CSS for selector: {}", selector);

        try {
            Locator locator = session.page().locator(selector);
            int elementCount = locator.count();

            if (elementCount == 0) {
                log.warn("No elements found for selector: {}", selector);
                return new CSSCollectionResult(
                        Collections.emptyList(),
                        Collections.emptyMap(),
                        0,
                        0,
                        Collections.emptyList()
                );
            }

            Locator element = locator.first();

            List<CSSRule> allRules = new ArrayList<>();
            Map<String, String> variables = new HashMap<>();
            List<String> externalStylesheets = new ArrayList<>();

            allRules.addAll(collectInlineStyles(element));

            Object result = collectMatchingRules(element);
            if (result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rulesData = (Map<String, Object>) result;

                @SuppressWarnings("unchecked")
                List<Map<String, String>> rulesList = (List<Map<String, String>>) rulesData.get("rules");
                if (rulesList != null) {
                    for (Map<String, String> ruleMap : rulesList) {
                        String sel = ruleMap.get("selector");
                        String decl = ruleMap.get("declarations");
                        String src = ruleMap.get("source");
                        String media = ruleMap.get("mediaQuery");

                        if (sel != null && decl != null) {
                            allRules.add(new CSSRule(sel, decl, 0, src, media));
                        }
                    }
                }

                @SuppressWarnings("unchecked")
                List<String> extSheets = (List<String>) rulesData.get("externalStylesheets");
                if (extSheets != null) {
                    externalStylesheets.addAll(extSheets);
                }
            }

            variables.putAll(collectCSSVariables(element));

            int totalRules = allRules.size();
            List<CSSRule> deduplicated = deduplicate(allRules);

            log.info("CSS collection completed: selector={}, totalRules={}, deduplicated={}, variables={}, externalSheets={}",
                    selector, totalRules, deduplicated.size(), variables.size(), externalStylesheets.size());

            return new CSSCollectionResult(
                    deduplicated,
                    variables,
                    totalRules,
                    deduplicated.size(),
                    externalStylesheets
            );

        } catch (Exception e) {
            log.error("Failed to collect CSS for selector: {}", selector, e);
            return new CSSCollectionResult(
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    0,
                    0,
                    Collections.emptyList()
            );
        }
    }

    private List<CSSRule> collectInlineStyles(Locator element) {
        String inlineStyle = element.getAttribute("style");

        if (inlineStyle == null || inlineStyle.isBlank()) {
            return Collections.emptyList();
        }

        String selector = getElementSelector(element);
        return List.of(new CSSRule(selector, inlineStyle, "inline"));
    }

    private Object collectMatchingRules(Locator element) {
        String script = """
                el => {
                    const rules = [];
                    const externalStylesheets = [];

                    try {
                        const sheets = Array.from(document.styleSheets);

                        for (const sheet of sheets) {
                            try {
                                const cssRules = Array.from(sheet.cssRules || []);

                                for (const rule of cssRules) {
                                    if (rule instanceof CSSStyleRule) {
                                        try {
                                            if (el.matches(rule.selectorText)) {
                                                rules.push({
                                                    selector: rule.selectorText,
                                                    declarations: rule.style.cssText,
                                                    source: sheet.href ? 'external' : 'internal',
                                                    mediaQuery: null
                                                });
                                            }
                                        } catch (e) {
                                            // Invalid selector or matching error
                                        }
                                    } else if (rule instanceof CSSMediaRule) {
                                        const mediaRules = Array.from(rule.cssRules);
                                        for (const mediaRule of mediaRules) {
                                            if (mediaRule instanceof CSSStyleRule) {
                                                try {
                                                    if (el.matches(mediaRule.selectorText)) {
                                                        rules.push({
                                                            selector: mediaRule.selectorText,
                                                            declarations: mediaRule.style.cssText,
                                                            source: sheet.href ? 'external' : 'internal',
                                                            mediaQuery: '@media ' + rule.conditionText
                                                        });
                                                    }
                                                } catch (e) {
                                                    // Invalid selector or matching error
                                                }
                                            }
                                        }
                                    }
                                }

                                if (sheet.href) {
                                    externalStylesheets.push(sheet.href);
                                }

                            } catch (e) {
                                // CORS error for external stylesheets
                                if (sheet.href) {
                                    externalStylesheets.push(sheet.href);
                                }
                            }
                        }
                    } catch (e) {
                        console.error('Error collecting CSS rules:', e);
                    }

                    return {
                        rules: rules,
                        externalStylesheets: externalStylesheets
                    };
                }
                """;

        return element.evaluate(script);
    }

    private Map<String, String> collectCSSVariables(Locator element) {
        String script = """
                el => {
                    const variables = {};

                    try {
                        const styles = window.getComputedStyle(el);

                        for (let i = 0; i < styles.length; i++) {
                            const prop = styles[i];
                            if (prop.startsWith('--')) {
                                const value = styles.getPropertyValue(prop).trim();
                                if (value) {
                                    variables[prop] = value;
                                }
                            }
                        }

                        const rootStyles = window.getComputedStyle(document.documentElement);
                        for (let i = 0; i < rootStyles.length; i++) {
                            const prop = rootStyles[i];
                            if (prop.startsWith('--')) {
                                const value = rootStyles.getPropertyValue(prop).trim();
                                if (value && !variables[prop]) {
                                    variables[prop] = value;
                                }
                            }
                        }
                    } catch (e) {
                        console.error('Error collecting CSS variables:', e);
                    }

                    return variables;
                }
                """;

        Object result = element.evaluate(script);

        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> vars = (Map<String, String>) result;
            return vars;
        }

        return Collections.emptyMap();
    }

    private List<CSSRule> deduplicate(List<CSSRule> rules) {
        Map<String, CSSRule> uniqueRules = new LinkedHashMap<>();

        for (CSSRule rule : rules) {
            String key = rule.getKey();
            uniqueRules.putIfAbsent(key, rule);
        }

        return new ArrayList<>(uniqueRules.values());
    }

    private String getElementSelector(Locator element) {
        String script = """
                el => {
                    if (el.id) {
                        return '#' + el.id;
                    }

                    let path = [];
                    let current = el;

                    while (current && current.nodeType === Node.ELEMENT_NODE) {
                        let selector = current.nodeName.toLowerCase();

                        if (current.className) {
                            selector += '.' + Array.from(current.classList).join('.');
                        }

                        path.unshift(selector);
                        current = current.parentElement;

                        if (path.length >= 3) break;
                    }

                    return path.join(' > ');
                }
                """;

        Object result = element.evaluate(script);
        return result != null ? result.toString() : "element";
    }
}
