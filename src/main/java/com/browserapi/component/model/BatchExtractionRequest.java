package com.browserapi.component.model;

import java.util.List;

/**
 * Request to extract multiple components from a single page.
 */
public record BatchExtractionRequest(
        String url,
        List<BatchComponentRequest> components,
        ExtractionOptions globalOptions
) {
    public BatchExtractionRequest(String url, List<BatchComponentRequest> components) {
        this(url, components, ExtractionOptions.defaults());
    }
}
