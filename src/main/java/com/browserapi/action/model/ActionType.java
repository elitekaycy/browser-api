package com.browserapi.action.model;

/**
 * Types of browser actions that can be executed.
 */
public enum ActionType {
    /**
     * Click an element on the page.
     */
    CLICK,

    /**
     * Fill an input field with text.
     */
    FILL,

    /**
     * Select an option from a dropdown.
     */
    SELECT,

    /**
     * Submit a form.
     */
    SUBMIT,

    /**
     * Wait for an element to appear.
     */
    WAIT,

    /**
     * Wait for page navigation to complete.
     */
    WAIT_NAVIGATION,

    /**
     * Scroll to an element.
     */
    SCROLL,

    /**
     * Hover over an element.
     */
    HOVER,

    /**
     * Press a keyboard key.
     */
    PRESS_KEY,

    /**
     * Press Enter key on an input field (for search boxes without forms).
     */
    PRESS_ENTER,

    /**
     * Take a screenshot.
     */
    SCREENSHOT,

    /**
     * Navigate to a URL.
     */
    NAVIGATE,

    /**
     * Check or uncheck a checkbox.
     */
    CHECK,

    /**
     * Clear an input field.
     */
    CLEAR,

    /**
     * Extract data from an element (text, HTML, attribute, or JSON).
     */
    EXTRACT
}
