package com.browserapi.component.model;

/**
 * Iframe sandbox attribute flags for security control.
 * Each flag grants specific permissions to the iframe content.
 */
public enum SandboxFlag {
    /**
     * Allow JavaScript execution inside the iframe.
     */
    ALLOW_SCRIPTS("allow-scripts"),

    /**
     * Allow form submission from the iframe.
     */
    ALLOW_FORMS("allow-forms"),

    /**
     * Allow iframe to access its own origin (cookies, localStorage, etc.).
     * Required for many interactive components.
     */
    ALLOW_SAME_ORIGIN("allow-same-origin"),

    /**
     * Allow popups (window.open, target="_blank", etc.).
     */
    ALLOW_POPUPS("allow-popups"),

    /**
     * Allow navigation to top-level browsing context.
     */
    ALLOW_TOP_NAVIGATION("allow-top-navigation"),

    /**
     * Allow pointer lock API.
     */
    ALLOW_POINTER_LOCK("allow-pointer-lock"),

    /**
     * Allow modal dialogs (alert, confirm, prompt).
     */
    ALLOW_MODALS("allow-modals");

    private final String value;

    SandboxFlag(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
