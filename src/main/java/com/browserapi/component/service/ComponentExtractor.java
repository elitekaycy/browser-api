package com.browserapi.component.service;

import com.browserapi.asset.model.Asset;
import com.browserapi.asset.service.AssetDetector;
import com.browserapi.asset.service.AssetDownloader;
import com.browserapi.asset.service.AssetInliner;
import com.browserapi.browser.BrowserManager;
import com.browserapi.browser.PageSession;
import com.browserapi.component.model.*;
import com.microsoft.playwright.Locator;
import com.browserapi.css.model.CSSCollectionResult;
import com.browserapi.css.model.ScopedCSSResult;
import com.browserapi.css.service.CSSCollector;
import com.browserapi.css.service.CSSScoper;
import com.browserapi.extraction.dto.ExtractionResponse;
import com.browserapi.extraction.dto.ExtractionRequest;
import com.browserapi.extraction.ExtractionType;
import com.browserapi.extraction.service.ExtractionService;
import com.browserapi.js.model.JavaScriptCollectionResult;
import com.browserapi.js.model.EncapsulatedJavaScript;
import com.browserapi.js.service.JavaScriptCollector;
import com.browserapi.js.service.JavaScriptEncapsulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates all extraction utilities to create complete, self-contained components.
 */
@Service
public class ComponentExtractor {

    private static final Logger log = LoggerFactory.getLogger(ComponentExtractor.class);

    private final BrowserManager browserManager;
    private final ExtractionService extractionService;
    private final CSSCollector cssCollector;
    private final CSSScoper cssScoper;
    private final JavaScriptCollector jsCollector;
    private final JavaScriptEncapsulator jsEncapsulator;
    private final AssetDetector assetDetector;
    private final AssetDownloader assetDownloader;
    private final AssetInliner assetInliner;
    private final ComponentCacheService cacheService;

    public ComponentExtractor(
            BrowserManager browserManager,
            ExtractionService extractionService,
            CSSCollector cssCollector,
            CSSScoper cssScoper,
            JavaScriptCollector jsCollector,
            JavaScriptEncapsulator jsEncapsulator,
            AssetDetector assetDetector,
            AssetDownloader assetDownloader,
            AssetInliner assetInliner,
            ComponentCacheService cacheService
    ) {
        this.browserManager = browserManager;
        this.extractionService = extractionService;
        this.cssCollector = cssCollector;
        this.cssScoper = cssScoper;
        this.jsCollector = jsCollector;
        this.jsEncapsulator = jsEncapsulator;
        this.assetDetector = assetDetector;
        this.assetDownloader = assetDownloader;
        this.assetInliner = assetInliner;
        this.cacheService = cacheService;
    }

    /**
     * Extracts a complete, self-contained component.
     *
     * @param url source URL
     * @param selector CSS selector
     * @param options extraction options
     * @return complete component ready to use
     */
    public CompleteComponent extract(String url, String selector, ExtractionOptions options) {
        log.info("Extracting complete component: url={}, selector={}", url, selector);

        // Check cache first
        String cacheKey = cacheService.generateCacheKey(url, selector, options, "JSON");
        Optional<CompleteComponent> cached = cacheService.getComponent(cacheKey);
        if (cached.isPresent()) {
            log.info("Returning cached component: url={}, selector={}", url, selector);
            return cached.get();
        }

        PageSession session = null;
        try {
            // Create browser session
            session = browserManager.createSession(url, options.waitStrategy());

            // Step 1: Extract HTML
            log.debug("Step 1: Extracting HTML");
            ExtractionRequest htmlRequest = new ExtractionRequest(url, ExtractionType.HTML, selector, options.waitStrategy());
            ExtractionResponse htmlResult = extractionService.extract(htmlRequest);
            String html = htmlResult.data();
            int htmlElements = countElements(html);

            // Step 2: Collect CSS
            log.debug("Step 2: Collecting CSS");
            CSSCollectionResult cssResult = cssCollector.collect(session, selector);
            String css = cssResult.toCSS();
            int cssRules = cssResult.deduplicatedRules();

            // Step 3: Collect JavaScript
            log.debug("Step 3: Collecting JavaScript");
            JavaScriptCollectionResult jsResult = jsCollector.collect(session, selector);
            String javascript = buildJavaScriptCode(jsResult);

            // Step 4: Detect and download assets
            log.debug("Step 4: Detecting and downloading assets");
            List<Asset> assets = assetDetector.detectAssets(session, selector);
            List<Asset> downloadedAssets = new ArrayList<>();
            List<String> failedAssets = new ArrayList<>();

            if (options.inlineAssets() && !assets.isEmpty()) {
                for (Asset asset : assets) {
                    // Filter by asset type if specified
                    if (options.assetTypes() != null &&
                        !options.assetTypes().isEmpty() &&
                        !options.assetTypes().contains(asset.type())) {
                        continue;
                    }

                    Asset downloaded = options.maxAssetSize() != null
                            ? assetDownloader.downloadAsset(session, asset, options.maxAssetSize())
                            : assetDownloader.downloadAsset(session, asset);

                    if (downloaded.hasData()) {
                        downloadedAssets.add(downloaded);
                    } else {
                        failedAssets.add(asset.resolvedUrl());
                    }
                }
            }

            // Step 5: Inline assets into HTML and CSS
            log.debug("Step 5: Inlining assets");
            if (options.inlineAssets() && !downloadedAssets.isEmpty()) {
                var inlinedResult = assetInliner.inline(html, css, downloadedAssets);
                html = inlinedResult.inlinedHtml();
                css = inlinedResult.inlinedCss();
            }

            // Step 6: Scope CSS
            log.debug("Step 6: Scoping CSS");
            String namespace = options.customNamespace();
            if (options.scopeCSS() && cssResult != null && cssResult.totalRules() > 0) {
                ScopedCSSResult scopedResult = cssScoper.scope(cssResult, namespace);
                css = scopedResult.scopedCSS();
                namespace = scopedResult.namespace();
            } else {
                namespace = namespace != null ? namespace : cssScoper.generateNamespace();
            }

            // Step 7: Encapsulate JavaScript
            log.debug("Step 7: Encapsulating JavaScript");
            if (options.encapsulateJS() && javascript != null && !javascript.isBlank()) {
                EncapsulatedJavaScript encapsulated = jsEncapsulator.encapsulate(
                        javascript,
                        "rootElement",
                        options.jsEncapsulationType()
                );
                javascript = encapsulated.encapsulatedCode();
            }

            // Calculate sizes
            long originalSize = html.length() + css.length() + javascript.length();
            long finalSize = html.length() + css.length() + javascript.length();

            // Build metadata and statistics
            ComponentMetadata metadata = new ComponentMetadata(
                    url,
                    selector,
                    downloadedAssets.size(),
                    downloadedAssets.stream()
                            .filter(a -> a.size() != null)
                            .mapToLong(Asset::size)
                            .sum(),
                    options
            );

            ExtractionStatistics statistics = new ExtractionStatistics(
                    htmlElements,
                    cssRules,
                    jsResult.totalListeners(),
                    jsResult.totalHandlers(),
                    jsResult.totalInlineScripts(),
                    jsResult.totalExternalScripts(),
                    downloadedAssets.size(),
                    failedAssets.size(),
                    originalSize,
                    finalSize
            );

            CompleteComponent component = new CompleteComponent(
                    html,
                    css,
                    javascript,
                    namespace,
                    metadata,
                    statistics
            );

            log.info("Component extraction completed: namespace={}, htmlElements={}, cssRules={}, assets={}",
                    namespace, htmlElements, cssRules, downloadedAssets.size());

            // Store in cache
            cacheService.putComponent(url, selector, options, component);

            return component;

        } catch (Exception e) {
            log.error("Component extraction failed: url={}, selector={}", url, selector, e);
            throw new RuntimeException("Component extraction failed: " + e.getMessage(), e);

        } finally {
            if (session != null) {
                try {
                    browserManager.closeSession(session.sessionId());
                } catch (Exception e) {
                    log.warn("Failed to close session", e);
                }
            }
        }
    }

    /**
     * Extracts multiple components in a single batch operation.
     * Uses one browser session for all extractions.
     *
     * @param request batch extraction request
     * @return batch result with all components
     */
    public BatchExtractionResult extractBatch(BatchExtractionRequest request) {
        log.info("Batch extraction request: url={}, components={}",
                request.url(), request.components().size());

        long startTime = System.currentTimeMillis();
        List<BatchComponentResult> results = new ArrayList<>();
        PageSession session = null;

        try {
            // Create single browser session for all extractions
            ExtractionOptions globalOptions = request.globalOptions() != null
                    ? request.globalOptions()
                    : ExtractionOptions.defaults();

            session = browserManager.createSession(request.url(), globalOptions.waitStrategy());

            // Extract each component
            for (BatchComponentRequest componentRequest : request.components()) {
                try {
                    // Merge global and component-specific options
                    ExtractionOptions options = mergeOptions(globalOptions, componentRequest.options());

                    if (componentRequest.multiple()) {
                        // Extract all matching elements
                        results.add(extractMultipleComponents(
                                session,
                                request.url(),
                                componentRequest,
                                options
                        ));
                    } else {
                        // Extract single component
                        CompleteComponent component = extractSingle(
                                session,
                                request.url(),
                                componentRequest.selector(),
                                options
                        );
                        results.add(BatchComponentResult.single(
                                componentRequest.name(),
                                componentRequest.selector(),
                                component
                        ));
                    }

                    log.debug("Extracted component: name={}, selector={}",
                            componentRequest.name(), componentRequest.selector());

                } catch (Exception e) {
                    log.error("Failed to extract component: name={}, selector={}",
                            componentRequest.name(), componentRequest.selector(), e);
                    results.add(BatchComponentResult.failed(
                            componentRequest.name(),
                            componentRequest.selector(),
                            e.getMessage()
                    ));
                }
            }

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Batch extraction completed: total={}, successful={}, failed={}, timeMs={}",
                    results.size(),
                    results.stream().filter(BatchComponentResult::success).count(),
                    results.stream().filter(r -> !r.success()).count(),
                    totalTime);

            return BatchExtractionResult.from(results, totalTime);

        } finally {
            if (session != null) {
                try {
                    browserManager.closeSession(session.sessionId());
                } catch (Exception e) {
                    log.warn("Failed to close session", e);
                }
            }
        }
    }

    /**
     * Extracts a single component using an existing session.
     */
    private CompleteComponent extractSingle(
            PageSession session,
            String url,
            String selector,
            ExtractionOptions options
    ) {
        // Similar to extract() but uses existing session
        // Step 1: Extract HTML
        ExtractionRequest htmlRequest = new ExtractionRequest(url, ExtractionType.HTML, selector, options.waitStrategy());
        ExtractionResponse htmlResult = extractionService.extract(htmlRequest);
        String html = htmlResult.data();
        int htmlElements = countElements(html);

        // Step 2: Collect CSS
        CSSCollectionResult cssResult = cssCollector.collect(session, selector);
        String css = cssResult.toCSS();
        int cssRules = cssResult.deduplicatedRules();

        // Step 3: Collect JavaScript
        JavaScriptCollectionResult jsResult = jsCollector.collect(session, selector);
        String javascript = buildJavaScriptCode(jsResult);

        // Step 4: Detect and download assets
        List<Asset> assets = assetDetector.detectAssets(session, selector);
        List<Asset> downloadedAssets = new ArrayList<>();

        if (options.inlineAssets() && !assets.isEmpty()) {
            for (Asset asset : assets) {
                if (options.assetTypes() != null &&
                    !options.assetTypes().isEmpty() &&
                    !options.assetTypes().contains(asset.type())) {
                    continue;
                }

                Asset downloaded = options.maxAssetSize() != null
                        ? assetDownloader.downloadAsset(session, asset, options.maxAssetSize())
                        : assetDownloader.downloadAsset(session, asset);

                if (downloaded.hasData()) {
                    downloadedAssets.add(downloaded);
                }
            }
        }

        // Step 5: Inline assets
        if (options.inlineAssets() && !downloadedAssets.isEmpty()) {
            var inlinedResult = assetInliner.inline(html, css, downloadedAssets);
            html = inlinedResult.inlinedHtml();
            css = inlinedResult.inlinedCss();
        }

        // Step 6: Scope CSS
        String namespace = options.customNamespace();
        if (options.scopeCSS() && cssResult != null && cssResult.totalRules() > 0) {
            ScopedCSSResult scopedResult = cssScoper.scope(cssResult, namespace);
            css = scopedResult.scopedCSS();
            namespace = scopedResult.namespace();
        } else {
            namespace = namespace != null ? namespace : cssScoper.generateNamespace();
        }

        // Step 7: Encapsulate JavaScript
        if (options.encapsulateJS() && javascript != null && !javascript.isBlank()) {
            EncapsulatedJavaScript encapsulated = jsEncapsulator.encapsulate(
                    javascript,
                    "rootElement",
                    options.jsEncapsulationType()
            );
            javascript = encapsulated.encapsulatedCode();
        }

        // Build result
        long originalSize = html.length() + css.length() + javascript.length();
        long finalSize = html.length() + css.length() + javascript.length();

        ComponentMetadata metadata = new ComponentMetadata(
                url,
                selector,
                downloadedAssets.size(),
                downloadedAssets.stream()
                        .filter(a -> a.size() != null)
                        .mapToLong(Asset::size)
                        .sum(),
                options
        );

        ExtractionStatistics statistics = new ExtractionStatistics(
                htmlElements,
                cssRules,
                jsResult.totalListeners(),
                jsResult.totalHandlers(),
                jsResult.totalInlineScripts(),
                jsResult.totalExternalScripts(),
                downloadedAssets.size(),
                0,
                originalSize,
                finalSize
        );

        return new CompleteComponent(html, css, javascript, namespace, metadata, statistics);
    }

    /**
     * Extracts multiple matching components.
     */
    private BatchComponentResult extractMultipleComponents(
            PageSession session,
            String url,
            BatchComponentRequest request,
            ExtractionOptions options
    ) {
        try {
            // Get count of matching elements
            int count = session.page().locator(request.selector()).count();

            if (count == 0) {
                return BatchComponentResult.failed(
                        request.name(),
                        request.selector(),
                        "No elements found"
                );
            }

            List<CompleteComponent> components = new ArrayList<>();

            // Extract each matching element
            for (int i = 0; i < count; i++) {
                // Use nth-of-type to target the i-th matching element
                String indexedSelector = request.selector() + ":nth-of-type(" + (i + 1) + ")";
                try {
                    CompleteComponent component = extractSingle(session, url, indexedSelector, options);
                    components.add(component);
                } catch (Exception e) {
                    log.warn("Failed to extract element {} of {}: {}", i + 1, count, e.getMessage());
                }
            }

            return BatchComponentResult.multiple(
                    request.name(),
                    request.selector(),
                    components
            );

        } catch (Exception e) {
            return BatchComponentResult.failed(
                    request.name(),
                    request.selector(),
                    e.getMessage()
            );
        }
    }

    /**
     * Merges global options with component-specific options.
     */
    private ExtractionOptions mergeOptions(ExtractionOptions global, ExtractionOptions component) {
        if (component == null) {
            return global;
        }

        return new ExtractionOptions(
                component.scopeCSS(),
                component.encapsulateJS(),
                component.inlineAssets(),
                component.maxAssetSize() != null ? component.maxAssetSize() : global.maxAssetSize(),
                component.assetTypes() != null ? component.assetTypes() : global.assetTypes(),
                component.customNamespace() != null ? component.customNamespace() : global.customNamespace(),
                component.waitStrategy() != null ? component.waitStrategy() : global.waitStrategy(),
                component.jsEncapsulationType() != null ? component.jsEncapsulationType() : global.jsEncapsulationType()
        );
    }

    /**
     * Builds JavaScript code from collection result.
     */
    private String buildJavaScriptCode(JavaScriptCollectionResult jsResult) {
        StringBuilder js = new StringBuilder();

        // Add inline handlers as event listeners
        if (!jsResult.inlineHandlers().isEmpty()) {
            js.append("// Inline handlers\n");
            jsResult.inlineHandlers().forEach(handler -> {
                js.append("// ").append(handler.attribute()).append("\n");
                js.append(handler.code()).append("\n\n");
            });
        }

        // Add inline scripts
        if (!jsResult.inlineScripts().isEmpty()) {
            js.append("// Inline scripts\n");
            jsResult.inlineScripts().forEach(script -> {
                js.append(script.content()).append("\n\n");
            });
        }

        // Note about external scripts
        if (!jsResult.externalScripts().isEmpty()) {
            js.append("// Note: ").append(jsResult.externalScripts().size())
                    .append(" external scripts detected but not included\n");
        }

        return js.toString().trim();
    }

    /**
     * Counts HTML elements in the markup.
     */
    private int countElements(String html) {
        if (html == null || html.isBlank()) {
            return 0;
        }
        // Simple count of opening tags
        return html.split("<[^/!][^>]*>").length - 1;
    }
}
