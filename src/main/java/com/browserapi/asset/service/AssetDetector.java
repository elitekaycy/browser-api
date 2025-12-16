package com.browserapi.asset.service;

import com.browserapi.asset.model.Asset;
import com.browserapi.browser.PageSession;
import com.microsoft.playwright.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects assets (images, fonts, videos, etc.) from HTML and CSS.
 */
@Service
public class AssetDetector {

    private static final Logger log = LoggerFactory.getLogger(AssetDetector.class);

    private static final Pattern CSS_URL_PATTERN = Pattern.compile(
            "url\\s*\\(\\s*['\"]?([^'\"\\)]+)['\"]?\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Detects all assets within a component.
     *
     * @param session browser session
     * @param selector CSS selector for the component
     * @return list of detected assets
     */
    public List<Asset> detectAssets(PageSession session, String selector) {
        log.debug("Detecting assets for selector: {}", selector);

        try {
            Locator locator = session.page().locator(selector);
            if (locator.count() == 0) {
                log.warn("No elements found for selector: {}", selector);
                return Collections.emptyList();
            }

            Locator element = locator.first();
            String baseUrl = session.page().url();

            List<Asset> assets = new ArrayList<>();

            // Detect HTML assets
            assets.addAll(detectHtmlImages(element, baseUrl));
            assets.addAll(detectHtmlVideos(element, baseUrl));
            assets.addAll(detectHtmlAudio(element, baseUrl));
            assets.addAll(detectHtmlIcons(element, baseUrl));

            // Detect CSS assets
            assets.addAll(detectCssAssets(element, baseUrl));

            log.info("Detected {} assets for selector: {}", assets.size(), selector);
            return assets;

        } catch (Exception e) {
            log.error("Failed to detect assets for selector: {}", selector, e);
            return Collections.emptyList();
        }
    }

    /**
     * Detects images from <img> and <picture> tags.
     */
    private List<Asset> detectHtmlImages(Locator element, String baseUrl) {
        List<Asset> assets = new ArrayList<>();

        try {
            // Find all img tags
            Locator imgTags = element.locator("img");
            int count = imgTags.count();

            for (int i = 0; i < count; i++) {
                Locator img = imgTags.nth(i);
                String src = img.getAttribute("src");
                String srcset = img.getAttribute("srcset");

                if (src != null && !src.isBlank()) {
                    String resolved = resolveUrl(baseUrl, src);
                    assets.add(new Asset(
                            src,
                            resolved,
                            Asset.AssetType.IMAGE,
                            guessMimeType(resolved)
                    ).withSource(Asset.AssetSource.HTML_IMG));
                }

                // Parse srcset attribute
                if (srcset != null && !srcset.isBlank()) {
                    assets.addAll(parseSrcset(srcset, baseUrl, Asset.AssetSource.HTML_IMG));
                }
            }

            // Find all source tags within picture
            Locator sourceTags = element.locator("picture > source");
            count = sourceTags.count();

            for (int i = 0; i < count; i++) {
                Locator source = sourceTags.nth(i);
                String srcset = source.getAttribute("srcset");

                if (srcset != null && !srcset.isBlank()) {
                    assets.addAll(parseSrcset(srcset, baseUrl, Asset.AssetSource.HTML_PICTURE));
                }
            }

        } catch (Exception e) {
            log.debug("Error detecting HTML images: {}", e.getMessage());
        }

        return assets;
    }

    /**
     * Detects videos from <video> tags.
     */
    private List<Asset> detectHtmlVideos(Locator element, String baseUrl) {
        List<Asset> assets = new ArrayList<>();

        try {
            Locator videoTags = element.locator("video");
            int count = videoTags.count();

            for (int i = 0; i < count; i++) {
                Locator video = videoTags.nth(i);
                String src = video.getAttribute("src");
                String poster = video.getAttribute("poster");

                if (src != null && !src.isBlank()) {
                    String resolved = resolveUrl(baseUrl, src);
                    assets.add(new Asset(
                            src,
                            resolved,
                            Asset.AssetType.VIDEO,
                            guessMimeType(resolved)
                    ).withSource(Asset.AssetSource.HTML_VIDEO));
                }

                if (poster != null && !poster.isBlank()) {
                    String resolved = resolveUrl(baseUrl, poster);
                    assets.add(new Asset(
                            poster,
                            resolved,
                            Asset.AssetType.IMAGE,
                            guessMimeType(resolved)
                    ).withSource(Asset.AssetSource.HTML_VIDEO));
                }
            }

            // Find source tags within video
            Locator sourceTags = element.locator("video > source");
            count = sourceTags.count();

            for (int i = 0; i < count; i++) {
                Locator source = sourceTags.nth(i);
                String src = source.getAttribute("src");

                if (src != null && !src.isBlank()) {
                    String resolved = resolveUrl(baseUrl, src);
                    assets.add(new Asset(
                            src,
                            resolved,
                            Asset.AssetType.VIDEO,
                            guessMimeType(resolved)
                    ).withSource(Asset.AssetSource.HTML_SOURCE));
                }
            }

        } catch (Exception e) {
            log.debug("Error detecting HTML videos: {}", e.getMessage());
        }

        return assets;
    }

    /**
     * Detects audio from <audio> tags.
     */
    private List<Asset> detectHtmlAudio(Locator element, String baseUrl) {
        List<Asset> assets = new ArrayList<>();

        try {
            Locator audioTags = element.locator("audio");
            int count = audioTags.count();

            for (int i = 0; i < count; i++) {
                Locator audio = audioTags.nth(i);
                String src = audio.getAttribute("src");

                if (src != null && !src.isBlank()) {
                    String resolved = resolveUrl(baseUrl, src);
                    assets.add(new Asset(
                            src,
                            resolved,
                            Asset.AssetType.AUDIO,
                            guessMimeType(resolved)
                    ).withSource(Asset.AssetSource.HTML_AUDIO));
                }
            }

            // Find source tags within audio
            Locator sourceTags = element.locator("audio > source");
            count = sourceTags.count();

            for (int i = 0; i < count; i++) {
                Locator source = sourceTags.nth(i);
                String src = source.getAttribute("src");

                if (src != null && !src.isBlank()) {
                    String resolved = resolveUrl(baseUrl, src);
                    assets.add(new Asset(
                            src,
                            resolved,
                            Asset.AssetType.AUDIO,
                            guessMimeType(resolved)
                    ).withSource(Asset.AssetSource.HTML_SOURCE));
                }
            }

        } catch (Exception e) {
            log.debug("Error detecting HTML audio: {}", e.getMessage());
        }

        return assets;
    }

    /**
     * Detects icons from <link rel="icon"> tags.
     */
    private List<Asset> detectHtmlIcons(Locator element, String baseUrl) {
        List<Asset> assets = new ArrayList<>();

        try {
            Locator linkTags = element.locator("link[rel*='icon']");
            int count = linkTags.count();

            for (int i = 0; i < count; i++) {
                Locator link = linkTags.nth(i);
                String href = link.getAttribute("href");

                if (href != null && !href.isBlank()) {
                    String resolved = resolveUrl(baseUrl, href);
                    assets.add(new Asset(
                            href,
                            resolved,
                            Asset.AssetType.ICON,
                            guessMimeType(resolved)
                    ).withSource(Asset.AssetSource.HTML_LINK_ICON));
                }
            }

        } catch (Exception e) {
            log.debug("Error detecting HTML icons: {}", e.getMessage());
        }

        return assets;
    }

    /**
     * Detects assets from CSS (backgrounds, fonts, etc.).
     */
    private List<Asset> detectCssAssets(Locator element, String baseUrl) {
        List<Asset> assets = new ArrayList<>();

        try {
            // Get all computed styles with background images
            String script = """
                    el => {
                        const urls = new Set();

                        // Check element itself
                        const style = window.getComputedStyle(el);
                        const bgImage = style.backgroundImage;
                        if (bgImage && bgImage !== 'none') {
                            const matches = bgImage.matchAll(/url\\(['"]?([^'"\\)]+)['"]?\\)/g);
                            for (const match of matches) {
                                urls.add(match[1]);
                            }
                        }

                        // Check all descendants
                        const descendants = el.querySelectorAll('*');
                        for (const desc of descendants) {
                            const descStyle = window.getComputedStyle(desc);
                            const descBgImage = descStyle.backgroundImage;
                            if (descBgImage && descBgImage !== 'none') {
                                const matches = descBgImage.matchAll(/url\\(['"]?([^'"\\)]+)['"]?\\)/g);
                                for (const match of matches) {
                                    urls.add(match[1]);
                                }
                            }
                        }

                        return Array.from(urls);
                    }
                    """;

            Object result = element.evaluate(script);

            if (result instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> urls = (List<String>) result;

                for (String url : urls) {
                    if (url != null && !url.isBlank() && !url.startsWith("data:")) {
                        String resolved = resolveUrl(baseUrl, url);
                        Asset.AssetType type = url.toLowerCase().contains("font")
                                ? Asset.AssetType.FONT
                                : Asset.AssetType.IMAGE;
                        assets.add(new Asset(
                                url,
                                resolved,
                                type,
                                guessMimeType(resolved)
                        ).withSource(Asset.AssetSource.CSS_BACKGROUND));
                    }
                }
            }

        } catch (Exception e) {
            log.debug("Error detecting CSS assets: {}", e.getMessage());
        }

        return assets;
    }

    /**
     * Parses srcset attribute to extract image URLs.
     */
    private List<Asset> parseSrcset(String srcset, String baseUrl, Asset.AssetSource source) {
        List<Asset> assets = new ArrayList<>();

        try {
            // srcset format: "url1 1x, url2 2x" or "url1 100w, url2 200w"
            String[] parts = srcset.split(",");

            for (String part : parts) {
                String trimmed = part.trim();
                // Extract just the URL (before any descriptor)
                String[] tokens = trimmed.split("\\s+");
                if (tokens.length > 0) {
                    String url = tokens[0];
                    if (!url.isBlank()) {
                        String resolved = resolveUrl(baseUrl, url);
                        assets.add(new Asset(
                                url,
                                resolved,
                                Asset.AssetType.IMAGE,
                                guessMimeType(resolved)
                        ).withSource(source));
                    }
                }
            }

        } catch (Exception e) {
            log.debug("Error parsing srcset: {}", e.getMessage());
        }

        return assets;
    }

    /**
     * Resolves relative URL to absolute URL.
     */
    private String resolveUrl(String baseUrl, String url) {
        try {
            // Already absolute
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:")) {
                return url;
            }

            URI baseUri = new URI(baseUrl);

            // Protocol-relative URL (//example.com/path)
            if (url.startsWith("//")) {
                return baseUri.getScheme() + ":" + url;
            }

            // Absolute path (/path/to/file)
            if (url.startsWith("/")) {
                return baseUri.getScheme() + "://" + baseUri.getAuthority() + url;
            }

            // Relative path (path/to/file or ../path/to/file)
            URI resolved = baseUri.resolve(url);
            return resolved.toString();

        } catch (Exception e) {
            log.debug("Failed to resolve URL: {} (base: {})", url, baseUrl);
            return url;
        }
    }

    /**
     * Guesses MIME type from URL extension.
     */
    private String guessMimeType(String url) {
        String lower = url.toLowerCase();

        // Images
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".ico")) return "image/x-icon";

        // Fonts
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".otf")) return "font/otf";
        if (lower.endsWith(".eot")) return "application/vnd.ms-fontobject";

        // Video
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".ogg")) return "video/ogg";

        // Audio
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".m4a")) return "audio/mp4";

        return "application/octet-stream";
    }
}
