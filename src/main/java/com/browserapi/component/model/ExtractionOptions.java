package com.browserapi.component.model;

import com.browserapi.asset.model.Asset;
import com.browserapi.browser.WaitStrategy;
import com.browserapi.js.model.EncapsulatedJavaScript;

import java.util.List;

/**
 * Configuration options for component extraction.
 */
public record ExtractionOptions(
        boolean scopeCSS,
        boolean encapsulateJS,
        boolean inlineAssets,
        Long maxAssetSize,
        List<Asset.AssetType> assetTypes,
        String customNamespace,
        WaitStrategy waitStrategy,
        EncapsulatedJavaScript.EncapsulationType jsEncapsulationType
) {
    public ExtractionOptions() {
        this(true, true, true, null, null, null, WaitStrategy.LOAD, EncapsulatedJavaScript.EncapsulationType.IIFE);
    }

    public ExtractionOptions(
            boolean scopeCSS,
            boolean encapsulateJS,
            boolean inlineAssets
    ) {
        this(scopeCSS, encapsulateJS, inlineAssets, null, null, null, WaitStrategy.LOAD, EncapsulatedJavaScript.EncapsulationType.IIFE);
    }

    public static ExtractionOptions defaults() {
        return new ExtractionOptions();
    }
}
