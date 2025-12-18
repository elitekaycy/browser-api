package com.browserapi.recorder.service;

import com.browserapi.action.model.Action;
import com.browserapi.action.model.ActionType;
import com.browserapi.browser.BrowserManager;
import com.browserapi.browser.PageSession;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for capturing user interactions in the browser and converting them to Actions.
 * <p>
 * Responsibilities:
 * - Generate and inject JavaScript event listeners into pages
 * - Capture DOM events (click, input, change, submit, keydown)
 * - Generate intelligent CSS selectors for elements
 * - Convert DOM events to Action objects
 * - Broadcast captured actions via WebSocket
 */
@Service
public class EventCaptureService {

    private static final Logger log = LoggerFactory.getLogger(EventCaptureService.class);

    /**
     * WebSocket messaging template for broadcasting captured actions.
     */
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Browser manager for accessing page sessions.
     */
    private final BrowserManager browserManager;

    /**
     * Track which sessions have event capture enabled.
     */
    private final Map<UUID, Boolean> captureEnabled;

    public EventCaptureService(SimpMessagingTemplate messagingTemplate, BrowserManager browserManager) {
        this.messagingTemplate = messagingTemplate;
        this.browserManager = browserManager;
        this.captureEnabled = new ConcurrentHashMap<>();
    }

    /**
     * Enable event capture for a recorder session.
     * Injects JavaScript event listeners into the page.
     *
     * @param sessionId recorder session ID
     * @param browserSessionId browser session ID
     */
    public void enableCapture(UUID sessionId, UUID browserSessionId) {
        if (captureEnabled.containsKey(sessionId)) {
            log.warn("Event capture already enabled for session: {}", sessionId);
            return;
        }

        log.info("Enabling event capture for session: {}", sessionId);

        PageSession pageSession = browserManager.getSession(browserSessionId)
                .orElseThrow(() -> new IllegalStateException("Browser session not found: " + browserSessionId));

        Page page = pageSession.page();

        // Expose callback function that JavaScript can call
        page.exposeFunction("__recorderCallback", args -> {
            handleCapturedEvent(sessionId, args);
            return null;
        });

        // Inject event capture script
        String script = generateEventCaptureScript();
        page.addInitScript(script);

        // Also evaluate immediately for current page
        page.evaluate(script);

        captureEnabled.put(sessionId, true);
        log.info("Event capture enabled for session: {}", sessionId);
    }

    /**
     * Disable event capture for a recorder session.
     *
     * @param sessionId recorder session ID
     */
    public void disableCapture(UUID sessionId) {
        captureEnabled.remove(sessionId);
        log.info("Event capture disabled for session: {}", sessionId);
    }

    /**
     * Handle an event captured from the browser.
     * Converts the event to an Action and broadcasts it.
     *
     * @param sessionId recorder session ID
     * @param eventData event data from JavaScript
     */
    private void handleCapturedEvent(UUID sessionId, Object eventData) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = (Map<String, Object>) eventData;

            String type = (String) event.get("type");
            String selector = (String) event.get("selector");
            String value = (String) event.get("value");

            log.debug("Captured event: type={}, selector={}", type, selector);

            // Convert to Action
            Action action = convertToAction(type, selector, value);

            if (action != null) {
                // Broadcast via WebSocket
                String destination = "/topic/recorder/" + sessionId + "/actions";
                messagingTemplate.convertAndSend(destination, action);

                log.debug("Action broadcasted: {}", action);
            }

        } catch (Exception e) {
            log.error("Error handling captured event", e);
        }
    }

    /**
     * Convert DOM event to Action object.
     *
     * @param eventType DOM event type (click, input, etc.)
     * @param selector CSS selector for the element
     * @param value event value (for input events)
     * @return Action object or null if event should be ignored
     */
    private Action convertToAction(String eventType, String selector, String value) {
        return switch (eventType) {
            case "click" -> Action.click(selector);
            case "input" -> Action.fill(selector, value != null ? value : "");
            case "change" -> {
                // For select elements
                if (value != null && !value.isEmpty()) {
                    yield Action.select(selector, value);
                }
                yield null;
            }
            case "submit" -> Action.submit(selector);
            case "keydown" -> {
                // Only capture Enter key presses
                if ("Enter".equals(value)) {
                    yield Action.pressKey(selector, "Enter");
                }
                yield null;
            }
            default -> {
                log.debug("Ignoring event type: {}", eventType);
                yield null;
            }
        };
    }

    /**
     * Generate JavaScript code for event capture.
     * This script is injected into the page and captures user interactions.
     *
     * @return JavaScript code as string
     */
    private String generateEventCaptureScript() {
        return """
            (function() {
                console.log('[Recorder] Event capture script initialized');

                // Generate unique CSS selector for an element
                function getSelector(element) {
                    // Try ID first (most reliable)
                    if (element.id) {
                        return '#' + element.id;
                    }

                    // Try name attribute for form elements
                    if (element.name) {
                        return element.tagName.toLowerCase() + '[name="' + element.name + '"]';
                    }

                    // Try unique class combination
                    if (element.className && typeof element.className === 'string') {
                        const classes = element.className.trim().split(/\\s+/).filter(c => c);
                        if (classes.length > 0) {
                            const selector = element.tagName.toLowerCase() + '.' + classes.join('.');
                            // Check if this selector is unique
                            if (document.querySelectorAll(selector).length === 1) {
                                return selector;
                            }
                        }
                    }

                    // Fall back to nth-child
                    let path = [];
                    let current = element;

                    while (current && current !== document.body) {
                        let selector = current.tagName.toLowerCase();

                        if (current.parentElement) {
                            const siblings = Array.from(current.parentElement.children);
                            const sameTagSiblings = siblings.filter(s => s.tagName === current.tagName);

                            if (sameTagSiblings.length > 1) {
                                const index = sameTagSiblings.indexOf(current) + 1;
                                selector += ':nth-of-type(' + index + ')';
                            }
                        }

                        path.unshift(selector);
                        current = current.parentElement;
                    }

                    return path.join(' > ');
                }

                // Debounce function to avoid too many events
                function debounce(func, wait) {
                    let timeout;
                    return function(...args) {
                        clearTimeout(timeout);
                        timeout = setTimeout(() => func.apply(this, args), wait);
                    };
                }

                // Click event listener
                document.addEventListener('click', function(e) {
                    const selector = getSelector(e.target);
                    window.__recorderCallback({
                        type: 'click',
                        selector: selector,
                        timestamp: Date.now()
                    });
                }, true);

                // Input event listener (debounced to avoid spam)
                const handleInput = debounce(function(e) {
                    const selector = getSelector(e.target);
                    window.__recorderCallback({
                        type: 'input',
                        selector: selector,
                        value: e.target.value,
                        timestamp: Date.now()
                    });
                }, 500);

                document.addEventListener('input', handleInput, true);

                // Change event listener (for select dropdowns)
                document.addEventListener('change', function(e) {
                    if (e.target.tagName === 'SELECT') {
                        const selector = getSelector(e.target);
                        window.__recorderCallback({
                            type: 'change',
                            selector: selector,
                            value: e.target.value,
                            timestamp: Date.now()
                        });
                    }
                }, true);

                // Submit event listener
                document.addEventListener('submit', function(e) {
                    const selector = getSelector(e.target);
                    window.__recorderCallback({
                        type: 'submit',
                        selector: selector,
                        timestamp: Date.now()
                    });
                }, true);

                // Keydown event listener (only capture Enter)
                document.addEventListener('keydown', function(e) {
                    if (e.key === 'Enter' && e.target.tagName === 'INPUT') {
                        const selector = getSelector(e.target);
                        window.__recorderCallback({
                            type: 'keydown',
                            selector: selector,
                            value: 'Enter',
                            timestamp: Date.now()
                        });
                    }
                }, true);

                console.log('[Recorder] Event listeners attached');
            })();
            """;
    }

    /**
     * Check if event capture is enabled for a session.
     *
     * @param sessionId recorder session ID
     * @return true if capture is enabled
     */
    public boolean isCaptureEnabled(UUID sessionId) {
        return captureEnabled.getOrDefault(sessionId, false);
    }
}
