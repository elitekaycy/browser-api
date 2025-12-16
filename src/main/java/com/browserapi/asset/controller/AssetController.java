package com.browserapi.asset.controller;

import com.browserapi.asset.model.Asset;
import com.browserapi.asset.model.AssetCollectionResult;
import com.browserapi.asset.model.InlinedComponentResult;
import com.browserapi.asset.service.AssetDetector;
import com.browserapi.asset.service.AssetDownloader;
import com.browserapi.asset.service.AssetInliner;
import com.browserapi.browser.BrowserManager;
import com.browserapi.browser.PageSession;
import com.browserapi.browser.WaitStrategy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for asset collection and inlining operations.
 */
@RestController
@RequestMapping("/api/v1/assets")
@Tag(name = "Asset Management", description = "Collect and inline component assets")
public class AssetController {

    private static final Logger log = LoggerFactory.getLogger(AssetController.class);

    private final BrowserManager browserManager;
    private final AssetDetector assetDetector;
    private final AssetDownloader assetDownloader;
    private final AssetInliner assetInliner;

    public AssetController(
            BrowserManager browserManager,
            AssetDetector assetDetector,
            AssetDownloader assetDownloader,
            AssetInliner assetInliner
    ) {
        this.browserManager = browserManager;
        this.assetDetector = assetDetector;
        this.assetDownloader = assetDownloader;
        this.assetInliner = assetInliner;
    }

    @GetMapping("/collect")
    @Operation(
            summary = "Collect assets from a component",
            description = """
                    Detects all assets (images, fonts, videos, etc.) used by a component:
                    - Parses HTML for img, video, audio, picture, source tags
                    - Detects CSS background images and fonts
                    - Resolves relative URLs to absolute URLs
                    - Categorizes assets by type

                    Example: GET /api/v1/assets/collect?url=https://example.com&selector=.header

                    Set download=true to also download and encode assets as Base64.
                    """
    )
    public ResponseEntity<?> collectAssets(
            @RequestParam String url,
            @RequestParam String selector,
            @RequestParam(required = false) WaitStrategy wait,
            @RequestParam(required = false, defaultValue = "false") boolean download,
            @RequestParam(required = false) Long maxSizeBytes
    ) {
        log.info("Asset collection request: url={}, selector={}, download={}",
                url, selector, download);

        PageSession session = null;
        try {
            session = browserManager.createSession(url, wait != null ? wait : WaitStrategy.LOAD);

            // Detect assets
            List<Asset> assets = assetDetector.detectAssets(session, selector);

            List<String> failedAssets = new ArrayList<>();

            // Optionally download assets
            if (download) {
                List<Asset> downloadedAssets = new ArrayList<>();

                for (Asset asset : assets) {
                    Asset downloaded = maxSizeBytes != null
                            ? assetDownloader.downloadAsset(session, asset, maxSizeBytes)
                            : assetDownloader.downloadAsset(session, asset);

                    if (downloaded.hasData()) {
                        downloadedAssets.add(downloaded);
                    } else {
                        failedAssets.add(asset.resolvedUrl());
                    }
                }

                assets = downloadedAssets;
            }

            AssetCollectionResult result = new AssetCollectionResult(assets, failedAssets);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Asset collection failed: url={}, selector={}", url, selector, e);
            return ResponseEntity
                    .internalServerError()
                    .body(Map.of("error", "Asset collection failed", "message", e.getMessage()));

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

    @PostMapping("/inline")
    @Operation(
            summary = "Inline assets into HTML and CSS",
            description = """
                    Replaces asset URLs with Base64 data URIs for self-contained components:
                    - Downloads all detected assets
                    - Encodes as Base64 data URIs
                    - Replaces URLs in HTML (src, srcset, href, poster)
                    - Replaces URLs in CSS (url() references)

                    Example request body:
                    {
                      "url": "https://example.com",
                      "selector": ".card",
                      "html": "<img src='/logo.png'>",
                      "css": "background: url('/bg.jpg')",
                      "maxSizeBytes": 1048576,
                      "assetTypes": ["IMAGE", "ICON"]
                    }

                    Returns inlined HTML/CSS with metadata about size impact.
                    """
    )
    public ResponseEntity<?> inlineAssets(@RequestBody InlineRequest request) {
        log.info("Asset inlining request: url={}, selector={}", request.url(), request.selector());

        PageSession session = null;
        try {
            session = browserManager.createSession(
                    request.url(),
                    request.waitStrategy() != null ? request.waitStrategy() : WaitStrategy.LOAD
            );

            // Detect assets
            List<Asset> assets = assetDetector.detectAssets(session, request.selector());

            // Download assets
            List<Asset> downloadedAssets = new ArrayList<>();
            List<String> failedAssets = new ArrayList<>();

            for (Asset asset : assets) {
                Asset downloaded = request.maxSizeBytes() != null
                        ? assetDownloader.downloadAsset(session, asset, request.maxSizeBytes())
                        : assetDownloader.downloadAsset(session, asset);

                if (downloaded.hasData()) {
                    downloadedAssets.add(downloaded);
                } else {
                    failedAssets.add(asset.resolvedUrl());
                }
            }

            // Inline assets
            InlinedComponentResult result;
            if (request.assetTypes() != null && !request.assetTypes().isEmpty()) {
                result = assetInliner.inlineTypes(
                        request.html(),
                        request.css(),
                        downloadedAssets,
                        request.assetTypes()
                );
            } else {
                result = assetInliner.inline(request.html(), request.css(), downloadedAssets);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Asset inlining failed: url={}, selector={}", request.url(), request.selector(), e);
            return ResponseEntity
                    .internalServerError()
                    .body(Map.of("error", "Asset inlining failed", "message", e.getMessage()));

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

    public record InlineRequest(
            String url,
            String selector,
            String html,
            String css,
            WaitStrategy waitStrategy,
            Long maxSizeBytes,
            List<Asset.AssetType> assetTypes
    ) {
    }
}
