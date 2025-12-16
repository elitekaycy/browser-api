package com.browserapi.cache.util;

import com.browserapi.extraction.dto.ExtractionRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates MD5 cache keys from extraction requests.
 * Cache key combines url, type, selector, waitStrategy, and sorted options.
 */
public class CacheKeyGenerator {

    /**
     * Generates a deterministic MD5 cache key from extraction request.
     * Same request parameters always produce the same key.
     *
     * @param request extraction request
     * @return 32-character MD5 hash
     */
    public static String generate(ExtractionRequest request) {
        String keyString = buildKeyString(request);
        return md5(keyString);
    }

    private static String buildKeyString(ExtractionRequest request) {
        return String.format("%s|%s|%s|%s|%s",
                request.url(),
                request.type(),
                request.selector(),
                request.waitStrategy(),
                serializeOptions(request.options())
        );
    }

    private static String serializeOptions(Map<String, Object> options) {
        if (options == null || options.isEmpty()) {
            return "";
        }

        return options.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
