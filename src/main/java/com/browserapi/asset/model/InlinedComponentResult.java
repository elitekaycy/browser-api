package com.browserapi.asset.model;

import java.util.List;

/**
 * Result of inlining assets into HTML and CSS.
 * Contains original and inlined versions with metadata.
 */
public record InlinedComponentResult(
        String originalHtml,
        String inlinedHtml,
        String originalCss,
        String inlinedCss,
        List<Asset> inlinedAssets,
        int totalReplacements,
        long totalInlinedSize,
        long estimatedSizeIncrease
) {
    public InlinedComponentResult(
            String originalHtml,
            String inlinedHtml,
            String originalCss,
            String inlinedCss,
            List<Asset> inlinedAssets,
            int totalReplacements
    ) {
        this(
                originalHtml,
                inlinedHtml,
                originalCss,
                inlinedCss,
                inlinedAssets,
                totalReplacements,
                calculateTotalSize(inlinedAssets),
                calculateSizeIncrease(originalHtml, inlinedHtml, originalCss, inlinedCss)
        );
    }

    private static long calculateTotalSize(List<Asset> assets) {
        return assets.stream()
                .filter(a -> a.size() != null)
                .mapToLong(Asset::size)
                .sum();
    }

    private static long calculateSizeIncrease(
            String originalHtml,
            String inlinedHtml,
            String originalCss,
            String inlinedCss
    ) {
        long originalSize = (originalHtml != null ? originalHtml.length() : 0)
                + (originalCss != null ? originalCss.length() : 0);
        long inlinedSize = (inlinedHtml != null ? inlinedHtml.length() : 0)
                + (inlinedCss != null ? inlinedCss.length() : 0);
        return inlinedSize - originalSize;
    }

    public boolean hasInlinedAssets() {
        return !inlinedAssets.isEmpty();
    }

    public double getSizeIncreasePercentage() {
        long originalSize = (originalHtml != null ? originalHtml.length() : 0)
                + (originalCss != null ? originalCss.length() : 0);
        if (originalSize == 0) {
            return 0.0;
        }
        return (estimatedSizeIncrease * 100.0) / originalSize;
    }
}
