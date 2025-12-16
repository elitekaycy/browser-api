package com.browserapi.js.model;

import java.util.List;

/**
 * Result of JavaScript collection operation.
 * Contains all JavaScript associated with a component.
 */
public record JavaScriptCollectionResult(
        List<JSEventListener> eventListeners,
        List<InlineHandler> inlineHandlers,
        List<InlineScript> inlineScripts,
        List<ExternalScript> externalScripts,
        int totalListeners,
        int totalHandlers,
        int totalInlineScripts,
        int totalExternalScripts
) {
    public JavaScriptCollectionResult(
            List<JSEventListener> eventListeners,
            List<InlineHandler> inlineHandlers,
            List<InlineScript> inlineScripts,
            List<ExternalScript> externalScripts
    ) {
        this(
                eventListeners,
                inlineHandlers,
                inlineScripts,
                externalScripts,
                eventListeners.size(),
                inlineHandlers.size(),
                inlineScripts.size(),
                externalScripts.size()
        );
    }

    public boolean hasJavaScript() {
        return totalListeners > 0 || totalHandlers > 0
            || totalInlineScripts > 0 || totalExternalScripts > 0;
    }
}
