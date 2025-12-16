package com.browserapi.asset.service;

import com.browserapi.asset.model.Asset;
import com.browserapi.browser.PageSession;
import com.microsoft.playwright.APIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Base64;

/**
 * Downloads assets from URLs using Playwright.
 * Respects authentication, cookies, and CORS.
 */
@Service
public class AssetDownloader {

    private static final Logger log = LoggerFactory.getLogger(AssetDownloader.class);

    private static final long MAX_ASSET_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int TIMEOUT_MS = 30000; // 30 seconds

    /**
     * Downloads an asset and returns it with Base64-encoded data.
     *
     * @param session browser session (for auth context)
     * @param asset asset to download
     * @return asset with data, or original asset if download fails
     */
    public Asset downloadAsset(PageSession session, Asset asset) {
        String url = asset.resolvedUrl();

        // Skip data URIs
        if (url.startsWith("data:")) {
            log.debug("Skipping data URI: {}", url.substring(0, Math.min(50, url.length())));
            return asset;
        }

        log.debug("Downloading asset: {}", url);

        try {
            // Use Playwright's API request context (respects cookies, auth)
            APIResponse response = session.page()
                    .request()
                    .get(url);

            if (!response.ok()) {
                log.warn("Failed to download asset: {} (status: {})", url, response.status());
                return asset;
            }

            byte[] data = response.body();

            // Check size limit
            if (data.length > MAX_ASSET_SIZE) {
                log.warn("Asset too large, skipping: {} (size: {} bytes)", url, data.length);
                return asset;
            }

            // Encode to Base64
            String base64Data = Base64.getEncoder().encodeToString(data);

            // Get actual MIME type from response
            String mimeType = response.headers().get("content-type");
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = asset.mimeType();
            } else {
                // Strip charset if present
                int semicolon = mimeType.indexOf(';');
                if (semicolon > 0) {
                    mimeType = mimeType.substring(0, semicolon).trim();
                }
            }

            Asset downloaded = new Asset(
                    asset.url(),
                    asset.resolvedUrl(),
                    asset.type(),
                    mimeType,
                    (long) data.length,
                    base64Data,
                    asset.source()
            );

            log.info("Downloaded asset: {} (size: {} bytes, mime: {})",
                    url, data.length, mimeType);

            return downloaded;

        } catch (Exception e) {
            log.error("Failed to download asset: {}", url, e);
            return asset;
        }
    }

    /**
     * Downloads an asset with a custom maximum size limit.
     *
     * @param session browser session
     * @param asset asset to download
     * @param maxSizeBytes maximum size in bytes
     * @return asset with data, or original asset if download fails
     */
    public Asset downloadAsset(PageSession session, Asset asset, long maxSizeBytes) {
        if (maxSizeBytes <= 0 || maxSizeBytes > MAX_ASSET_SIZE) {
            return downloadAsset(session, asset);
        }

        String url = asset.resolvedUrl();

        if (url.startsWith("data:")) {
            return asset;
        }

        log.debug("Downloading asset with size limit {}: {}", maxSizeBytes, url);

        try {
            APIResponse response = session.page()
                    .request()
                    .get(url);

            if (!response.ok()) {
                log.warn("Failed to download asset: {} (status: {})", url, response.status());
                return asset;
            }

            byte[] data = response.body();

            if (data.length > maxSizeBytes) {
                log.warn("Asset exceeds size limit, skipping: {} (size: {} bytes, limit: {} bytes)",
                        url, data.length, maxSizeBytes);
                return asset;
            }

            String base64Data = Base64.getEncoder().encodeToString(data);

            String mimeType = response.headers().get("content-type");
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = asset.mimeType();
            } else {
                int semicolon = mimeType.indexOf(';');
                if (semicolon > 0) {
                    mimeType = mimeType.substring(0, semicolon).trim();
                }
            }

            return new Asset(
                    asset.url(),
                    asset.resolvedUrl(),
                    asset.type(),
                    mimeType,
                    (long) data.length,
                    base64Data,
                    asset.source()
            );

        } catch (Exception e) {
            log.error("Failed to download asset: {}", url, e);
            return asset;
        }
    }
}
