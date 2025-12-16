package com.browserapi.js.model;

/**
 * Represents an external script reference.
 * Example: <script src="/app.js" async defer></script>
 */
public record ExternalScript(
        String src,
        String type,
        boolean async,
        boolean defer,
        String integrity,
        String crossorigin
) {
    public ExternalScript(String src) {
        this(src, "text/javascript", false, false, null, null);
    }

    public ExternalScript(String src, String type, boolean async, boolean defer) {
        this(src, type, async, defer, null, null);
    }

    public boolean isModule() {
        return "module".equalsIgnoreCase(type);
    }
}
