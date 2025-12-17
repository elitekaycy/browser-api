package com.browserapi.component.model;

/**
 * Supported export formats for components.
 */
public enum ExportFormat {
    /**
     * Standalone HTML file ready to open in browser.
     */
    HTML,

    /**
     * React component with JSX and separate CSS file.
     */
    REACT,

    /**
     * Vue Single File Component (.vue).
     */
    VUE,

    /**
     * Web Component with Shadow DOM.
     */
    WEB_COMPONENT,

    /**
     * Raw JSON data (default API response).
     */
    JSON;

    /**
     * Returns the file extension for this format.
     */
    public String getExtension() {
        return switch (this) {
            case HTML -> ".html";
            case REACT -> ".jsx";
            case VUE -> ".vue";
            case WEB_COMPONENT -> ".js";
            case JSON -> ".json";
        };
    }

    /**
     * Returns the MIME type for this format.
     */
    public String getMimeType() {
        return switch (this) {
            case HTML -> "text/html";
            case REACT -> "text/javascript";
            case VUE -> "text/plain";
            case WEB_COMPONENT -> "text/javascript";
            case JSON -> "application/json";
        };
    }
}
