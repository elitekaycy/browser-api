package com.browserapi.asset.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Result of asset collection operation.
 * Contains all detected assets from HTML and CSS.
 */
public record AssetCollectionResult(
        List<Asset> assets,
        int totalAssets,
        long totalSize,
        List<String> failedAssets,
        Map<Asset.AssetType, Long> assetsByType
) {
    public AssetCollectionResult(List<Asset> assets, List<String> failedAssets) {
        this(
                assets,
                assets.size(),
                calculateTotalSize(assets),
                failedAssets,
                groupByType(assets)
        );
    }

    private static long calculateTotalSize(List<Asset> assets) {
        return assets.stream()
                .filter(a -> a.size() != null)
                .mapToLong(Asset::size)
                .sum();
    }

    private static Map<Asset.AssetType, Long> groupByType(List<Asset> assets) {
        return assets.stream()
                .collect(Collectors.groupingBy(
                        Asset::type,
                        Collectors.counting()
                ));
    }

    public boolean hasFailures() {
        return !failedAssets.isEmpty();
    }

    public long getSuccessCount() {
        return assets.stream()
                .filter(Asset::hasData)
                .count();
    }
}
