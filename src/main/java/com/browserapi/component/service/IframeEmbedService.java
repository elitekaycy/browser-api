package com.browserapi.component.service;

import com.browserapi.component.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Service for generating iframe embed codes.
 * Creates ready-to-use HTML snippets for embedding hosted components.
 */
@Service
public class IframeEmbedService {

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${component.hosting.base-url:/hosted}")
    private String baseUrl;

    /**
     * Generates all embed code variants for a hosted component.
     *
     * @param fileId the component file ID
     * @param publicUrl the public URL path (e.g., /hosted/abc123.html)
     * @return embed code with fixed, responsive, and minimal variants
     */
    public EmbedCode generateEmbedCodes(String fileId, String publicUrl) {
        String fullUrl = buildFullUrl(publicUrl);

        String fixed = generateFixedEmbed(fullUrl, 800, 600);
        String responsive = generateResponsiveEmbed(fullUrl);
        String minimal = generateMinimalEmbed(fullUrl);

        return EmbedCode.create(fixed, responsive, minimal);
    }

    /**
     * Generates a single embed code with custom options.
     *
     * @param publicUrl the public URL path
     * @param options embed options
     * @return generated iframe HTML
     */
    public String generateEmbed(String publicUrl, EmbedOptions options) {
        String fullUrl = buildFullUrl(publicUrl);

        return switch (options.type()) {
            case FIXED -> generateFixedEmbed(fullUrl, options.width(), options.height(), options);
            case RESPONSIVE -> generateResponsiveEmbed(fullUrl, options);
            case MINIMAL -> generateMinimalEmbed(fullUrl);
        };
    }

    /**
     * Generates a fixed-size iframe embed.
     */
    private String generateFixedEmbed(String url, int width, int height) {
        return generateFixedEmbed(url, width, height, EmbedOptions.fixed());
    }

    /**
     * Generates a fixed-size iframe embed with custom options.
     */
    private String generateFixedEmbed(String url, Integer width, Integer height, EmbedOptions options) {
        int w = width != null ? width : 800;
        int h = height != null ? height : 600;

        StringBuilder html = new StringBuilder();
        html.append("<iframe\n");
        html.append("  src=\"").append(escapeHtml(url)).append("\"\n");
        html.append("  width=\"").append(w).append("\"\n");
        html.append("  height=\"").append(h).append("\"\n");
        html.append("  frameborder=\"0\"\n");

        if (options.sandbox()) {
            html.append("  sandbox=\"").append(buildSandboxAttribute(options.sandboxFlags())).append("\"\n");
        }

        html.append("  title=\"Extracted Component\"\n");
        html.append("  loading=\"lazy\">\n");
        html.append("</iframe>");

        return html.toString();
    }

    /**
     * Generates a responsive iframe embed.
     * Uses aspect ratio padding technique (16:9 default).
     */
    private String generateResponsiveEmbed(String url) {
        return generateResponsiveEmbed(url, EmbedOptions.responsive());
    }

    /**
     * Generates a responsive iframe embed with custom options.
     */
    private String generateResponsiveEmbed(String url, EmbedOptions options) {
        // Calculate aspect ratio (default 16:9 = 56.25%)
        double aspectRatio = 56.25;
        if (options.width() != null && options.height() != null) {
            aspectRatio = ((double) options.height() / options.width()) * 100;
        }

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"position: relative; padding-bottom: ").append(String.format("%.2f", aspectRatio)).append("%; height: 0; overflow: hidden;\">\n");
        html.append("  <iframe\n");
        html.append("    src=\"").append(escapeHtml(url)).append("\"\n");
        html.append("    style=\"position: absolute; top: 0; left: 0; width: 100%; height: 100%;\"\n");
        html.append("    frameborder=\"0\"\n");

        if (options.sandbox()) {
            html.append("    sandbox=\"").append(buildSandboxAttribute(options.sandboxFlags())).append("\"\n");
        }

        html.append("    title=\"Extracted Component\"\n");
        html.append("    loading=\"lazy\">\n");
        html.append("  </iframe>\n");
        html.append("</div>");

        return html.toString();
    }

    /**
     * Generates a minimal iframe embed.
     */
    private String generateMinimalEmbed(String url) {
        return String.format("<iframe src=\"%s\" frameborder=\"0\"></iframe>", escapeHtml(url));
    }

    /**
     * Builds the sandbox attribute value from flags.
     */
    private String buildSandboxAttribute(java.util.Set<SandboxFlag> flags) {
        if (flags == null || flags.isEmpty()) {
            return "";
        }

        return flags.stream()
                .map(SandboxFlag::getValue)
                .collect(Collectors.joining(" "));
    }

    /**
     * Builds a full URL from a path.
     * Uses localhost for development, will use actual host in production.
     */
    private String buildFullUrl(String path) {
        // If path already starts with http, return as-is
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }

        // Build URL with protocol and host
        // In production, this should use the actual server URL
        return "http://localhost:" + serverPort + path;
    }

    /**
     * Escapes HTML special characters.
     */
    private String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
