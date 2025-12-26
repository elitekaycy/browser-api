package com.browserapi.action.model;

/**
 * Represents a browser action to be executed.
 */
public record Action(
        ActionType type,
        String selector,
        String value,
        Integer waitMs,
        String description,
        String extractType,     // TEXT, HTML, ATTRIBUTE, JSON (for EXTRACT action)
        String attributeName,   // For ATTRIBUTE extraction type
        String jsonPath         // For JSON extraction type (JSONPath expression)
) {
    /**
     * Click an element.
     */
    public static Action click(String selector) {
        return new Action(ActionType.CLICK, selector, null, null, "Click " + selector, null, null, null);
    }

    /**
     * Click with custom description.
     */
    public static Action click(String selector, String description) {
        return new Action(ActionType.CLICK, selector, null, null, description, null, null, null);
    }

    /**
     * Fill an input field.
     */
    public static Action fill(String selector, String value) {
        return new Action(ActionType.FILL, selector, value, null, "Fill " + selector + " with '" + value + "'", null, null, null);
    }

    /**
     * Fill with custom description.
     */
    public static Action fill(String selector, String value, String description) {
        return new Action(ActionType.FILL, selector, value, null, description, null, null, null);
    }

    /**
     * Select a dropdown option by value.
     */
    public static Action select(String selector, String value) {
        return new Action(ActionType.SELECT, selector, value, null, "Select '" + value + "' in " + selector, null, null, null);
    }

    /**
     * Submit a form.
     */
    public static Action submit(String selector) {
        return new Action(ActionType.SUBMIT, selector, null, null, "Submit form " + selector, null, null, null);
    }

    /**
     * Wait for an element to appear.
     */
    public static Action waitFor(String selector) {
        return new Action(ActionType.WAIT, selector, null, null, "Wait for " + selector, null, null, null);
    }

    /**
     * Wait for an element with timeout.
     */
    public static Action waitFor(String selector, int timeoutMs) {
        return new Action(ActionType.WAIT, selector, null, timeoutMs, "Wait for " + selector + " (" + timeoutMs + "ms)", null, null, null);
    }

    /**
     * Wait for navigation.
     */
    public static Action waitNavigation() {
        return new Action(ActionType.WAIT_NAVIGATION, null, null, null, "Wait for navigation", null, null, null);
    }

    /**
     * Wait for navigation with timeout.
     */
    public static Action waitNavigation(int timeoutMs) {
        return new Action(ActionType.WAIT_NAVIGATION, null, null, timeoutMs, "Wait for navigation (" + timeoutMs + "ms)", null, null, null);
    }

    /**
     * Scroll to an element.
     */
    public static Action scroll(String selector) {
        return new Action(ActionType.SCROLL, selector, null, null, "Scroll to " + selector, null, null, null);
    }

    /**
     * Hover over an element.
     */
    public static Action hover(String selector) {
        return new Action(ActionType.HOVER, selector, null, null, "Hover over " + selector, null, null, null);
    }

    /**
     * Press a keyboard key.
     */
    public static Action pressKey(String key) {
        return new Action(ActionType.PRESS_KEY, null, key, null, "Press key '" + key + "'", null, null, null);
    }

    /**
     * Press a key on a specific element.
     */
    public static Action pressKey(String selector, String key) {
        return new Action(ActionType.PRESS_KEY, selector, key, null, "Press key '" + key + "' on " + selector, null, null, null);
    }

    /**
     * Take a screenshot.
     */
    public static Action screenshot() {
        return new Action(ActionType.SCREENSHOT, null, null, null, "Take screenshot", null, null, null);
    }

    /**
     * Navigate to a URL.
     */
    public static Action navigate(String url) {
        return new Action(ActionType.NAVIGATE, null, url, null, "Navigate to " + url, null, null, null);
    }

    /**
     * Check a checkbox.
     */
    public static Action check(String selector) {
        return new Action(ActionType.CHECK, selector, "true", null, "Check " + selector, null, null, null);
    }

    /**
     * Uncheck a checkbox.
     */
    public static Action uncheck(String selector) {
        return new Action(ActionType.CHECK, selector, "false", null, "Uncheck " + selector, null, null, null);
    }

    /**
     * Clear an input field.
     */
    public static Action clear(String selector) {
        return new Action(ActionType.CLEAR, selector, null, null, "Clear " + selector, null, null, null);
    }

    /**
     * Wait for a specific duration.
     */
    public static Action sleep(int milliseconds) {
        return new Action(ActionType.WAIT, null, null, milliseconds, "Sleep for " + milliseconds + "ms", null, null, null);
    }

    /**
     * Extract text content from an element.
     */
    public static Action extractText(String selector) {
        return new Action(ActionType.EXTRACT, selector, null, null, "Extract text from " + selector, "TEXT", null, null);
    }

    /**
     * Extract HTML content from an element.
     */
    public static Action extractHtml(String selector) {
        return new Action(ActionType.EXTRACT, selector, null, null, "Extract HTML from " + selector, "HTML", null, null);
    }

    /**
     * Extract an attribute from an element.
     */
    public static Action extractAttribute(String selector, String attributeName) {
        return new Action(ActionType.EXTRACT, selector, null, null, "Extract attribute '" + attributeName + "' from " + selector, "ATTRIBUTE", attributeName, null);
    }

    /**
     * Extract JSON data from an element.
     */
    public static Action extractJson(String selector) {
        return new Action(ActionType.EXTRACT, selector, null, null, "Extract JSON from " + selector, "JSON", null, null);
    }

    /**
     * Extract JSON data with JSONPath expression.
     */
    public static Action extractJson(String selector, String jsonPath) {
        return new Action(ActionType.EXTRACT, selector, null, null, "Extract JSON from " + selector + " using path " + jsonPath, "JSON", null, jsonPath);
    }
}
