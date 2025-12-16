package com.browserapi.asset.model;

/**
 * Represents a web asset (image, font, video, etc.).
 */
public record Asset(
        String url,
        String resolvedUrl,
        AssetType type,
        String mimeType,
        Long size,
        String base64Data,
        AssetSource source
) {
    public Asset(String url, String resolvedUrl, AssetType type, String mimeType) {
        this(url, resolvedUrl, type, mimeType, null, null, AssetSource.UNKNOWN);
    }

    public Asset withData(String base64Data, Long size) {
        return new Asset(url, resolvedUrl, type, mimeType, size, base64Data, source);
    }

    public Asset withSource(AssetSource source) {
        return new Asset(url, resolvedUrl, type, mimeType, size, base64Data, source);
    }

    public boolean hasData() {
        return base64Data != null && !base64Data.isBlank();
    }

    public String toDataUri() {
        if (!hasData()) {
            return null;
        }
        return String.format("data:%s;base64,%s", mimeType, base64Data);
    }

    public enum AssetType {
        IMAGE,
        FONT,
        VIDEO,
        AUDIO,
        ICON,
        STYLESHEET,
        OTHER
    }

    public enum AssetSource {
        HTML_IMG,
        HTML_PICTURE,
        HTML_VIDEO,
        HTML_AUDIO,
        HTML_SOURCE,
        HTML_LINK_ICON,
        CSS_BACKGROUND,
        CSS_FONT_FACE,
        CSS_URL,
        UNKNOWN
    }
}
