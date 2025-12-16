package com.browserapi.js.service;

import com.browserapi.js.model.EncapsulatedJavaScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encapsulates JavaScript code to prevent naming conflicts and scope pollution.
 * Wraps code in IIFE and rewrites document queries to be scoped to a root element.
 */
@Service
public class JavaScriptEncapsulator {

    private static final Logger log = LoggerFactory.getLogger(JavaScriptEncapsulator.class);

    private static final String DEFAULT_ROOT_VARIABLE = "rootElement";

    // Patterns for document query methods
    private static final Pattern QUERY_SELECTOR_PATTERN = Pattern.compile(
            "document\\.querySelector\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern QUERY_SELECTOR_ALL_PATTERN = Pattern.compile(
            "document\\.querySelectorAll\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern GET_ELEMENT_BY_ID_PATTERN = Pattern.compile(
            "document\\.getElementById\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern GET_ELEMENTS_BY_CLASS_PATTERN = Pattern.compile(
            "document\\.getElementsByClassName\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern GET_ELEMENTS_BY_TAG_PATTERN = Pattern.compile(
            "document\\.getElementsByTagName\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for async/await detection
    private static final Pattern ASYNC_PATTERN = Pattern.compile(
            "\\basync\\s+function\\b|\\basync\\s*\\(|\\bawait\\s+",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Encapsulates JavaScript code with default settings.
     *
     * @param code original JavaScript code
     * @return encapsulated result
     */
    public EncapsulatedJavaScript encapsulate(String code) {
        return encapsulate(code, DEFAULT_ROOT_VARIABLE, EncapsulatedJavaScript.EncapsulationType.IIFE);
    }

    /**
     * Encapsulates JavaScript code with custom root variable and encapsulation type.
     *
     * @param code original JavaScript code
     * @param rootVariable variable name for root element
     * @param type encapsulation type
     * @return encapsulated result
     */
    public EncapsulatedJavaScript encapsulate(
            String code,
            String rootVariable,
            EncapsulatedJavaScript.EncapsulationType type
    ) {
        log.debug("Encapsulating JavaScript: {} chars, rootVar={}, type={}",
                code.length(), rootVariable, type);

        if (code == null || code.isBlank()) {
            log.warn("Empty code provided for encapsulation");
            return new EncapsulatedJavaScript(
                    code,
                    code,
                    rootVariable,
                    List.of(),
                    false,
                    type
            );
        }

        List<String> replacements = new ArrayList<>();
        String scopedCode = rewriteDocumentQueries(code, rootVariable, replacements);
        boolean hasAsync = detectAsyncCode(code);

        String encapsulatedCode = switch (type) {
            case IIFE -> wrapInIIFE(scopedCode, rootVariable, hasAsync);
            case MODULE -> wrapInModule(scopedCode, rootVariable);
            case CLOSURE -> wrapInClosure(scopedCode, rootVariable, hasAsync);
        };

        log.info("JavaScript encapsulated: {} replacements, async={}", replacements.size(), hasAsync);

        return new EncapsulatedJavaScript(
                code,
                encapsulatedCode,
                rootVariable,
                replacements,
                hasAsync,
                type
        );
    }

    /**
     * Rewrites document query methods to be scoped to root element.
     */
    private String rewriteDocumentQueries(String code, String rootVariable, List<String> replacements) {
        String result = code;

        // document.querySelector() -> rootElement.querySelector()
        result = replacePattern(
                result,
                QUERY_SELECTOR_PATTERN,
                rootVariable + ".querySelector(",
                "document.querySelector",
                replacements
        );

        // document.querySelectorAll() -> rootElement.querySelectorAll()
        result = replacePattern(
                result,
                QUERY_SELECTOR_ALL_PATTERN,
                rootVariable + ".querySelectorAll(",
                "document.querySelectorAll",
                replacements
        );

        // document.getElementById() -> rootElement.querySelector('#id')
        result = replaceGetElementById(result, rootVariable, replacements);

        // document.getElementsByClassName() -> rootElement.getElementsByClassName()
        result = replacePattern(
                result,
                GET_ELEMENTS_BY_CLASS_PATTERN,
                rootVariable + ".getElementsByClassName(",
                "document.getElementsByClassName",
                replacements
        );

        // document.getElementsByTagName() -> rootElement.getElementsByTagName()
        result = replacePattern(
                result,
                GET_ELEMENTS_BY_TAG_PATTERN,
                rootVariable + ".getElementsByTagName(",
                "document.getElementsByTagName",
                replacements
        );

        return result;
    }

    /**
     * Replaces pattern occurrences and tracks replacements.
     */
    private String replacePattern(
            String code,
            Pattern pattern,
            String replacement,
            String originalText,
            List<String> replacements
    ) {
        Matcher matcher = pattern.matcher(code);
        int count = 0;
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            count++;
        }
        matcher.appendTail(sb);

        if (count > 0) {
            replacements.add(originalText + " (" + count + "x)");
        }

        return sb.toString();
    }

    /**
     * Special handling for getElementById - converts to querySelector with #id.
     */
    private String replaceGetElementById(String code, String rootVariable, List<String> replacements) {
        Pattern pattern = Pattern.compile(
                "document\\.getElementById\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(code);
        int count = 0;
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String id = matcher.group(1);
            String replacement = rootVariable + ".querySelector('#" + id + "')";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            count++;
        }
        matcher.appendTail(sb);

        if (count > 0) {
            replacements.add("document.getElementById -> querySelector with # (" + count + "x)");
        }

        return sb.toString();
    }

    /**
     * Detects if code contains async/await.
     */
    private boolean detectAsyncCode(String code) {
        return ASYNC_PATTERN.matcher(code).find();
    }

    /**
     * Wraps code in an Immediately Invoked Function Expression (IIFE).
     */
    private String wrapInIIFE(String code, String rootVariable, boolean isAsync) {
        String asyncKeyword = isAsync ? "async " : "";

        return String.format("""
                ((%sfunction(%s) {
                  %s
                })(document.currentScript?.parentElement || document.body));
                """,
                asyncKeyword,
                rootVariable,
                indentCode(code, 2)
        ).trim();
    }

    /**
     * Wraps code in an ES6 module structure.
     */
    private String wrapInModule(String code, String rootVariable) {
        return String.format("""
                (function() {
                  const %s = document.currentScript?.parentElement || document.body;

                  %s
                })();
                """,
                rootVariable,
                indentCode(code, 2)
        ).trim();
    }

    /**
     * Wraps code in a closure with explicit context.
     */
    private String wrapInClosure(String code, String rootVariable, boolean isAsync) {
        String asyncKeyword = isAsync ? "async " : "";

        return String.format("""
                (function() {
                  const %s = document.currentScript?.parentElement || document.body;

                  return (%sfunction() {
                    %s
                  })();
                })();
                """,
                rootVariable,
                asyncKeyword,
                indentCode(code, 4)
        ).trim();
    }

    /**
     * Indents code by specified number of spaces.
     */
    private String indentCode(String code, int spaces) {
        String indent = " ".repeat(spaces);
        return code.lines()
                .map(line -> line.isBlank() ? line : indent + line)
                .reduce((a, b) -> a + "\n" + b)
                .orElse(code);
    }
}
