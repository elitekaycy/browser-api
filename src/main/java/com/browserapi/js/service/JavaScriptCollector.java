package com.browserapi.js.service;

import com.browserapi.browser.PageSession;
import com.browserapi.js.model.*;
import com.microsoft.playwright.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Collects all JavaScript associated with a component.
 * Includes event listeners, inline handlers, inline scripts, and external scripts.
 */
@Service
public class JavaScriptCollector {

    private static final Logger log = LoggerFactory.getLogger(JavaScriptCollector.class);

    private static final List<String> EVENT_ATTRIBUTES = List.of(
            "onclick", "ondblclick", "onmousedown", "onmouseup", "onmouseover",
            "onmouseout", "onmousemove", "onkeydown", "onkeyup", "onkeypress",
            "onload", "onunload", "onchange", "onsubmit", "onreset", "onfocus",
            "onblur", "oninput", "onselect", "onscroll", "onresize", "ontouchstart",
            "ontouchend", "ontouchmove", "ontouchcancel", "oncontextmenu", "ondrag",
            "ondragstart", "ondragend", "ondragenter", "ondragleave", "ondragover",
            "ondrop"
    );

    /**
     * Collects all JavaScript for elements matching the selector.
     *
     * @param session browser session
     * @param selector CSS selector
     * @return collection result with all JavaScript types
     */
    public JavaScriptCollectionResult collect(PageSession session, String selector) {
        log.debug("Collecting JavaScript for selector: {}", selector);

        try {
            Locator locator = session.page().locator(selector);
            int elementCount = locator.count();

            if (elementCount == 0) {
                log.warn("No elements found for selector: {}", selector);
                return new JavaScriptCollectionResult(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList()
                );
            }

            Locator element = locator.first();

            List<JSEventListener> eventListeners = extractEventListeners(element);
            List<InlineHandler> inlineHandlers = extractInlineHandlers(element);
            List<InlineScript> inlineScripts = extractInlineScripts(element);
            List<ExternalScript> externalScripts = extractExternalScripts(element);

            log.info("JavaScript collection completed: selector={}, listeners={}, handlers={}, inlineScripts={}, externalScripts={}",
                    selector, eventListeners.size(), inlineHandlers.size(),
                    inlineScripts.size(), externalScripts.size());

            return new JavaScriptCollectionResult(
                    eventListeners,
                    inlineHandlers,
                    inlineScripts,
                    externalScripts
            );

        } catch (Exception e) {
            log.error("Failed to collect JavaScript for selector: {}", selector, e);
            return new JavaScriptCollectionResult(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }
    }

    /**
     * Extracts event listeners attached via addEventListener().
     * Note: This requires Chrome DevTools API or monkey-patching.
     * Returns empty list if not available (best effort).
     */
    private List<JSEventListener> extractEventListeners(Locator element) {
        try {
            String script = """
                    el => {
                        const listeners = [];

                        // Try Chrome DevTools API (only available in DevTools context)
                        if (typeof getEventListeners === 'function') {
                            const allListeners = getEventListeners(el);

                            for (const [eventType, handlers] of Object.entries(allListeners)) {
                                for (const handler of handlers) {
                                    listeners.push({
                                        eventType: eventType,
                                        listenerCode: handler.listener.toString(),
                                        useCapture: handler.useCapture || false,
                                        passive: handler.passive || false
                                    });
                                }
                            }
                        }

                        // Try custom tracking (if injected)
                        if (window.__eventListeners && window.__eventListeners.has(el)) {
                            const tracked = window.__eventListeners.get(el);
                            for (const item of tracked) {
                                listeners.push({
                                    eventType: item.type,
                                    listenerCode: item.listener,
                                    useCapture: item.options?.capture || false,
                                    passive: item.options?.passive || false
                                });
                            }
                        }

                        return listeners;
                    }
                    """;

            Object result = element.evaluate(script);

            if (result instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> listenersList = (List<Map<String, Object>>) result;

                return listenersList.stream()
                        .map(map -> new JSEventListener(
                                (String) map.get("eventType"),
                                (String) map.get("listenerCode"),
                                (Boolean) map.getOrDefault("useCapture", false),
                                (Boolean) map.getOrDefault("passive", false)
                        ))
                        .toList();
            }

        } catch (Exception e) {
            log.debug("Could not extract event listeners (API not available): {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    /**
     * Extracts inline event handler attributes (onclick, onload, etc.).
     */
    private List<InlineHandler> extractInlineHandlers(Locator element) {
        List<InlineHandler> handlers = new ArrayList<>();

        for (String attr : EVENT_ATTRIBUTES) {
            try {
                String code = element.getAttribute(attr);
                if (code != null && !code.isBlank()) {
                    handlers.add(new InlineHandler(attr, code));
                    log.debug("Found inline handler: {}=\"{}\"", attr, code);
                }
            } catch (Exception e) {
                log.debug("Could not check attribute {}: {}", attr, e.getMessage());
            }
        }

        // Also check for child elements with inline handlers
        try {
            String script = """
                    el => {
                        const handlers = [];
                        const eventAttrs = ['onclick', 'ondblclick', 'onmousedown', 'onmouseup',
                                           'onload', 'onchange', 'onsubmit', 'onfocus', 'onblur'];

                        // Check all descendants
                        const descendants = el.querySelectorAll('*');
                        for (const desc of descendants) {
                            for (const attr of eventAttrs) {
                                if (desc.hasAttribute(attr)) {
                                    handlers.push({
                                        attribute: attr,
                                        code: desc.getAttribute(attr)
                                    });
                                }
                            }
                        }

                        return handlers;
                    }
                    """;

            Object result = element.evaluate(script);

            if (result instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> handlersList = (List<Map<String, String>>) result;

                for (Map<String, String> map : handlersList) {
                    String attr = map.get("attribute");
                    String code = map.get("code");
                    if (attr != null && code != null && !code.isBlank()) {
                        handlers.add(new InlineHandler(attr, code));
                    }
                }
            }

        } catch (Exception e) {
            log.debug("Could not extract child inline handlers: {}", e.getMessage());
        }

        return handlers;
    }

    /**
     * Extracts inline <script> tags within the component.
     */
    private List<InlineScript> extractInlineScripts(Locator element) {
        List<InlineScript> scripts = new ArrayList<>();

        try {
            Locator scriptTags = element.locator("script:not([src])");
            int count = scriptTags.count();

            log.debug("Found {} inline script tags", count);

            for (int i = 0; i < count; i++) {
                Locator script = scriptTags.nth(i);
                String content = script.textContent();
                String type = script.getAttribute("type");

                if (content != null && !content.isBlank()) {
                    scripts.add(new InlineScript(
                            content.trim(),
                            type != null ? type : "text/javascript"
                    ));
                }
            }

        } catch (Exception e) {
            log.debug("Could not extract inline scripts: {}", e.getMessage());
        }

        return scripts;
    }

    /**
     * Extracts external <script src=""> references.
     */
    private List<ExternalScript> extractExternalScripts(Locator element) {
        List<ExternalScript> scripts = new ArrayList<>();

        try {
            Locator scriptTags = element.locator("script[src]");
            int count = scriptTags.count();

            log.debug("Found {} external script tags", count);

            for (int i = 0; i < count; i++) {
                Locator script = scriptTags.nth(i);

                String src = script.getAttribute("src");
                String type = script.getAttribute("type");
                boolean async = script.getAttribute("async") != null;
                boolean defer = script.getAttribute("defer") != null;
                String integrity = script.getAttribute("integrity");
                String crossorigin = script.getAttribute("crossorigin");

                if (src != null && !src.isBlank()) {
                    scripts.add(new ExternalScript(
                            src,
                            type != null ? type : "text/javascript",
                            async,
                            defer,
                            integrity,
                            crossorigin
                    ));
                }
            }

        } catch (Exception e) {
            log.debug("Could not extract external scripts: {}", e.getMessage());
        }

        return scripts;
    }
}
