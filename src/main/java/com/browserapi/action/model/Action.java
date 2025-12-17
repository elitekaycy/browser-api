package com.browserapi.action.model;

/**
 * Represents a browser action to be executed.
 */
public record Action(
        ActionType type,
        String selector,
        String value,
        Integer waitMs,
        String description
) {
    /**
     * Click an element.
     */
    public static Action click(String selector) {
        return new Action(ActionType.CLICK, selector, null, null, "Click " + selector);
    }

    /**
     * Click with custom description.
     */
    public static Action click(String selector, String description) {
        return new Action(ActionType.CLICK, selector, null, null, description);
    }

    /**
     * Fill an input field.
     */
    public static Action fill(String selector, String value) {
        return new Action(ActionType.FILL, selector, value, null, "Fill " + selector + " with '" + value + "'");
    }

    /**
     * Fill with custom description.
     */
    public static Action fill(String selector, String value, String description) {
        return new Action(ActionType.FILL, selector, value, null, description);
    }

    /**
     * Select a dropdown option by value.
     */
    public static Action select(String selector, String value) {
        return new Action(ActionType.SELECT, selector, value, null, "Select '" + value + "' in " + selector);
    }

    /**
     * Submit a form.
     */
    public static Action submit(String selector) {
        return new Action(ActionType.SUBMIT, selector, null, null, "Submit form " + selector);
    }

    /**
     * Wait for an element to appear.
     */
    public static Action waitFor(String selector) {
        return new Action(ActionType.WAIT, selector, null, null, "Wait for " + selector);
    }

    /**
     * Wait for an element with timeout.
     */
    public static Action waitFor(String selector, int timeoutMs) {
        return new Action(ActionType.WAIT, selector, null, timeoutMs, "Wait for " + selector + " (" + timeoutMs + "ms)");
    }

    /**
     * Wait for navigation.
     */
    public static Action waitNavigation() {
        return new Action(ActionType.WAIT_NAVIGATION, null, null, null, "Wait for navigation");
    }

    /**
     * Wait for navigation with timeout.
     */
    public static Action waitNavigation(int timeoutMs) {
        return new Action(ActionType.WAIT_NAVIGATION, null, null, timeoutMs, "Wait for navigation (" + timeoutMs + "ms)");
    }

    /**
     * Scroll to an element.
     */
    public static Action scroll(String selector) {
        return new Action(ActionType.SCROLL, selector, null, null, "Scroll to " + selector);
    }

    /**
     * Hover over an element.
     */
    public static Action hover(String selector) {
        return new Action(ActionType.HOVER, selector, null, null, "Hover over " + selector);
    }

    /**
     * Press a keyboard key.
     */
    public static Action pressKey(String key) {
        return new Action(ActionType.PRESS_KEY, null, key, null, "Press key '" + key + "'");
    }

    /**
     * Press a key on a specific element.
     */
    public static Action pressKey(String selector, String key) {
        return new Action(ActionType.PRESS_KEY, selector, key, null, "Press key '" + key + "' on " + selector);
    }

    /**
     * Take a screenshot.
     */
    public static Action screenshot() {
        return new Action(ActionType.SCREENSHOT, null, null, null, "Take screenshot");
    }

    /**
     * Navigate to a URL.
     */
    public static Action navigate(String url) {
        return new Action(ActionType.NAVIGATE, null, url, null, "Navigate to " + url);
    }

    /**
     * Check a checkbox.
     */
    public static Action check(String selector) {
        return new Action(ActionType.CHECK, selector, "true", null, "Check " + selector);
    }

    /**
     * Uncheck a checkbox.
     */
    public static Action uncheck(String selector) {
        return new Action(ActionType.CHECK, selector, "false", null, "Uncheck " + selector);
    }

    /**
     * Clear an input field.
     */
    public static Action clear(String selector) {
        return new Action(ActionType.CLEAR, selector, null, null, "Clear " + selector);
    }

    /**
     * Wait for a specific duration.
     */
    public static Action sleep(int milliseconds) {
        return new Action(ActionType.WAIT, null, null, milliseconds, "Sleep for " + milliseconds + "ms");
    }
}
