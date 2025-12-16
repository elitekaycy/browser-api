package com.browserapi.css.service;

import com.browserapi.css.model.CSSCollectionResult;
import com.browserapi.css.model.CSSRule;
import com.browserapi.css.model.ScopedCSSResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Scopes CSS to prevent conflicts when extracting components.
 * Adds unique namespace to all selectors and renames keyframes.
 */
@Service
public class CSSScoper {

    private static final Logger log = LoggerFactory.getLogger(CSSScoper.class);
    private static final String NAMESPACE_PREFIX = "component";

    /**
     * Scopes CSS rules with a unique namespace.
     * If namespace is null, generates one automatically.
     *
     * @param collectionResult CSS collection result
     * @param namespace optional namespace (auto-generated if null)
     * @return scoped CSS result
     */
    public ScopedCSSResult scope(CSSCollectionResult collectionResult, String namespace) {
        if (namespace == null || namespace.isBlank()) {
            namespace = generateNamespace();
        }

        log.debug("Scoping CSS with namespace: {}", namespace);

        List<CSSRule> scopedRules = new ArrayList<>();
        Map<String, String> renamedKeyframes = new HashMap<>();

        for (CSSRule rule : collectionResult.rules()) {
            String scopedSelector = scopeSelector(rule.selector(), namespace);
            String scopedDeclarations = rule.declarations();

            scopedRules.add(new CSSRule(
                    scopedSelector,
                    scopedDeclarations,
                    rule.specificity(),
                    rule.source(),
                    rule.mediaQuery()
            ));
        }

        String scopedCSS = formatScopedCSS(scopedRules, collectionResult.variables(), namespace);

        scopedCSS = scopeKeyframes(scopedCSS, namespace, renamedKeyframes);

        log.info("CSS scoping completed: namespace={}, originalRules={}, scopedRules={}, keyframesRenamed={}",
                namespace, collectionResult.totalRules(), scopedRules.size(), renamedKeyframes.size());

        return new ScopedCSSResult(
                namespace,
                scopedCSS,
                scopedRules,
                renamedKeyframes,
                collectionResult.totalRules(),
                scopedRules.size()
        );
    }

    /**
     * Generates a unique namespace for component isolation.
     */
    public String generateNamespace() {
        return NAMESPACE_PREFIX + "-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);
    }

    /**
     * Scopes a CSS selector by adding namespace prefix.
     * Handles comma-separated selectors and pseudo-selectors.
     */
    private String scopeSelector(String selector, String namespace) {
        if (selector == null || selector.isBlank()) {
            return selector;
        }

        String[] selectors = selector.split(",");

        return Arrays.stream(selectors)
                .map(sel -> scopeSingleSelector(sel.trim(), namespace))
                .collect(Collectors.joining(", "));
    }

    /**
     * Scopes a single selector, preserving pseudo-selectors.
     */
    private String scopeSingleSelector(String selector, String namespace) {
        if (isGlobalSelector(selector)) {
            log.debug("Skipping global selector: {}", selector);
            return selector;
        }

        int pseudoIndex = findPseudoIndex(selector);

        if (pseudoIndex > 0) {
            String base = selector.substring(0, pseudoIndex);
            String pseudo = selector.substring(pseudoIndex);
            return "." + namespace + " " + base + pseudo;
        }

        return "." + namespace + " " + selector;
    }

    /**
     * Checks if selector should not be scoped (global selectors).
     */
    private boolean isGlobalSelector(String selector) {
        selector = selector.trim().toLowerCase();

        return selector.startsWith(":root")
                || selector.startsWith("html")
                || selector.startsWith("body")
                || selector.equals("*")
                || selector.startsWith("@");
    }

    /**
     * Finds the index of the first pseudo-selector (: or ::).
     */
    private int findPseudoIndex(String selector) {
        int singleColon = selector.indexOf(':');
        if (singleColon == -1) {
            return -1;
        }

        if (singleColon + 1 < selector.length()
                && selector.charAt(singleColon + 1) == ':') {
            return singleColon;
        }

        return singleColon;
    }

    /**
     * Scopes @keyframes by renaming them with namespace prefix.
     * Also updates animation references.
     */
    private String scopeKeyframes(String css, String namespace, Map<String, String> renamedKeyframes) {
        Pattern keyframesPattern = Pattern.compile(
                "@keyframes\\s+([\\w-]+)\\s*\\{",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = keyframesPattern.matcher(css);
        Set<String> keyframeNames = new HashSet<>();

        while (matcher.find()) {
            String originalName = matcher.group(1);
            keyframeNames.add(originalName);
        }

        for (String originalName : keyframeNames) {
            String scopedName = namespace + "-" + originalName;
            renamedKeyframes.put(originalName, scopedName);

            css = css.replaceAll(
                    "@keyframes\\s+" + Pattern.quote(originalName) + "\\s*\\{",
                    "@keyframes " + scopedName + " {"
            );

            css = css.replaceAll(
                    "\\banimation:\\s*([^;]*\\b)" + Pattern.quote(originalName) + "\\b",
                    "animation: $1" + scopedName
            );
            css = css.replaceAll(
                    "\\banimation-name:\\s*" + Pattern.quote(originalName) + "\\b",
                    "animation-name: " + scopedName
            );
        }

        return css;
    }

    /**
     * Formats scoped CSS rules into CSS text.
     */
    private String formatScopedCSS(List<CSSRule> rules, Map<String, String> variables, String namespace) {
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
            css.append("/* Scoped Component Styles (namespace: .").append(namespace).append(") */\n");

            Map<String, List<CSSRule>> rulesByMedia = new LinkedHashMap<>();
            rulesByMedia.put(null, new ArrayList<>());

            for (CSSRule rule : rules) {
                String mediaQuery = rule.mediaQuery();
                rulesByMedia.computeIfAbsent(mediaQuery, k -> new ArrayList<>()).add(rule);
            }

            for (Map.Entry<String, List<CSSRule>> entry : rulesByMedia.entrySet()) {
                String mediaQuery = entry.getKey();
                List<CSSRule> mediaRules = entry.getValue();

                if (mediaQuery != null) {
                    css.append(mediaQuery).append(" {\n");
                }

                for (CSSRule rule : mediaRules) {
                    if (mediaQuery != null) {
                        css.append("  ");
                    }
                    css.append(rule.selector()).append(" {\n");

                    String[] declarations = rule.declarations().split(";");
                    for (String decl : declarations) {
                        decl = decl.trim();
                        if (!decl.isEmpty()) {
                            if (mediaQuery != null) {
                                css.append("  ");
                            }
                            css.append("  ").append(decl).append(";\n");
                        }
                    }

                    if (mediaQuery != null) {
                        css.append("  ");
                    }
                    css.append("}\n\n");
                }

                if (mediaQuery != null) {
                    css.append("}\n\n");
                }
            }
        }

        return css.toString().trim();
    }
}
