package com.browserapi.component.model;

/**
 * Statistics about the extraction process.
 */
public record ExtractionStatistics(
        int htmlElements,
        int cssRules,
        int jsListeners,
        int jsInlineHandlers,
        int jsInlineScripts,
        int jsExternalScripts,
        int inlinedAssets,
        int failedAssets,
        long originalSize,
        long finalSize,
        double compressionRatio
) {
    public ExtractionStatistics(
            int htmlElements,
            int cssRules,
            int jsListeners,
            int jsInlineHandlers,
            int jsInlineScripts,
            int jsExternalScripts,
            int inlinedAssets,
            int failedAssets,
            long originalSize,
            long finalSize
    ) {
        this(
                htmlElements,
                cssRules,
                jsListeners,
                jsInlineHandlers,
                jsInlineScripts,
                jsExternalScripts,
                inlinedAssets,
                failedAssets,
                originalSize,
                finalSize,
                calculateCompressionRatio(originalSize, finalSize)
        );
    }

    private static double calculateCompressionRatio(long originalSize, long finalSize) {
        if (originalSize == 0) {
            return 0.0;
        }
        return ((double) finalSize / originalSize) * 100.0;
    }
}
