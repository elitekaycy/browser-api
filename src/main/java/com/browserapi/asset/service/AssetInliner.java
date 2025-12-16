package com.browserapi.asset.service;

import com.browserapi.asset.model.Asset;
import com.browserapi.asset.model.InlinedComponentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inlines assets by replacing URLs with Base64 data URIs.
 */
@Service
public class AssetInliner {

    private static final Logger log = LoggerFactory.getLogger(AssetInliner.class);

    /**
     * Inlines assets into HTML and CSS content.
     *
     * @param html original HTML
     * @param css original CSS
     * @param assets list of assets with Base64 data
     * @return inlined result
     */
    public InlinedComponentResult inline(String html, String css, List<Asset> assets) {
        log.debug("Inlining {} assets into HTML/CSS", assets.size());

        List<Asset> inlinedAssets = new ArrayList<>();
        int replacementCount = 0;

        String inlinedHtml = html;
        String inlinedCss = css;

        // Process each asset
        for (Asset asset : assets) {
            if (!asset.hasData()) {
                log.debug("Skipping asset without data: {}", asset.url());
                continue;
            }

            String dataUri = asset.toDataUri();
            String originalUrl = asset.url();

            int htmlReplacements = 0;
            int cssReplacements = 0;

            // Replace in HTML
            if (inlinedHtml != null && !inlinedHtml.isBlank()) {
                String beforeHtml = inlinedHtml;
                inlinedHtml = replaceInHtml(inlinedHtml, originalUrl, dataUri);
                if (!inlinedHtml.equals(beforeHtml)) {
                    htmlReplacements++;
                }
            }

            // Replace in CSS
            if (inlinedCss != null && !inlinedCss.isBlank()) {
                String beforeCss = inlinedCss;
                inlinedCss = replaceInCss(inlinedCss, originalUrl, dataUri);
                if (!inlinedCss.equals(beforeCss)) {
                    cssReplacements++;
                }
            }

            int totalReplacements = htmlReplacements + cssReplacements;
            if (totalReplacements > 0) {
                inlinedAssets.add(asset);
                replacementCount += totalReplacements;
                log.debug("Inlined asset: {} ({} replacements)", originalUrl, totalReplacements);
            }
        }

        log.info("Inlined {} assets with {} total replacements", inlinedAssets.size(), replacementCount);

        return new InlinedComponentResult(
                html,
                inlinedHtml,
                css,
                inlinedCss,
                inlinedAssets,
                replacementCount
        );
    }

    /**
     * Replaces asset URL in HTML with data URI.
     * Handles src, srcset, href, and poster attributes.
     */
    private String replaceInHtml(String html, String url, String dataUri) {
        String result = html;

        // Escape special regex characters in URL
        String escapedUrl = Pattern.quote(url);

        // Replace in src="url"
        result = result.replaceAll(
                "src\\s*=\\s*['\"]" + escapedUrl + "['\"]",
                Matcher.quoteReplacement("src=\"" + dataUri + "\"")
        );

        // Replace in href="url"
        result = result.replaceAll(
                "href\\s*=\\s*['\"]" + escapedUrl + "['\"]",
                Matcher.quoteReplacement("href=\"" + dataUri + "\"")
        );

        // Replace in poster="url"
        result = result.replaceAll(
                "poster\\s*=\\s*['\"]" + escapedUrl + "['\"]",
                Matcher.quoteReplacement("poster=\"" + dataUri + "\"")
        );

        // Replace in srcset (more complex, needs to preserve descriptors)
        result = replaceInSrcset(result, url, dataUri);

        return result;
    }

    /**
     * Replaces asset URL in srcset attribute.
     * Format: srcset="url1 1x, url2 2x" or srcset="url1 100w, url2 200w"
     */
    private String replaceInSrcset(String html, String url, String dataUri) {
        String escapedUrl = Pattern.quote(url);

        // Match srcset attribute
        Pattern srcsetPattern = Pattern.compile(
                "srcset\\s*=\\s*['\"]([^'\"]*)['\"]",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = srcsetPattern.matcher(html);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String srcsetValue = matcher.group(1);

            // Replace URL within srcset value while preserving descriptors
            String replaced = srcsetValue.replaceAll(
                    escapedUrl + "(\\s+[0-9.]+[xw])?",
                    Matcher.quoteReplacement(dataUri) + "$1"
            );

            matcher.appendReplacement(sb,
                    Matcher.quoteReplacement("srcset=\"" + replaced + "\""));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Replaces asset URL in CSS with data URI.
     * Handles url() references in backgrounds, fonts, etc.
     */
    private String replaceInCss(String css, String url, String dataUri) {
        String result = css;

        // Escape special regex characters
        String escapedUrl = Pattern.quote(url);

        // Replace url("url")
        result = result.replaceAll(
                "url\\s*\\(\\s*['\"]?" + escapedUrl + "['\"]?\\s*\\)",
                Matcher.quoteReplacement("url(\"" + dataUri + "\")")
        );

        return result;
    }

    /**
     * Inlines only specific asset types.
     *
     * @param html original HTML
     * @param css original CSS
     * @param assets list of assets
     * @param allowedTypes types to inline
     * @return inlined result
     */
    public InlinedComponentResult inlineTypes(
            String html,
            String css,
            List<Asset> assets,
            List<Asset.AssetType> allowedTypes
    ) {
        log.debug("Inlining assets with allowed types: {}", allowedTypes);

        List<Asset> filtered = assets.stream()
                .filter(asset -> allowedTypes.contains(asset.type()))
                .toList();

        return inline(html, css, filtered);
    }
}
