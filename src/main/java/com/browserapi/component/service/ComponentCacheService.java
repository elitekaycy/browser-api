package com.browserapi.component.service;

import com.browserapi.component.entity.CachedComponent;
import com.browserapi.component.model.*;
import com.browserapi.component.repository.CachedComponentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for caching complete component extractions.
 * <p>
 * Key features:
 * <ul>
 *   <li>Dedicated TTL for components (longer than basic extractions)</li>
 *   <li>Size limits to prevent storing huge components</li>
 *   <li>Access statistics tracking</li>
 *   <li>Format-specific caching (JSON, HTML, REACT, etc.)</li>
 * </ul>
 */
@Service
public class ComponentCacheService {

    private static final Logger log = LoggerFactory.getLogger(ComponentCacheService.class);
    private static final long DEFAULT_MAX_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
    private static final int DEFAULT_TTL_SECONDS = 3600; // 1 hour

    private final CachedComponentRepository repository;
    private final ObjectMapper objectMapper;

    @Value("${component.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${component.cache.ttl:3600}")
    private int ttlSeconds;

    @Value("${component.cache.max-size-bytes:10485760}")
    private long maxSizeBytes;

    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    public ComponentCacheService(CachedComponentRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Retrieves a cached component.
     *
     * @param cacheKey unique cache key
     * @return cached component if found and not expired
     */
    @Transactional
    public Optional<CompleteComponent> getComponent(String cacheKey) {
        if (!cacheEnabled) {
            log.debug("Component cache disabled");
            return Optional.empty();
        }

        Optional<CachedComponent> cached = repository
                .findByCacheKeyAndExpiresAtAfter(cacheKey, LocalDateTime.now());

        if (cached.isPresent()) {
            CachedComponent entry = cached.get();
            entry.incrementAccessCount();
            repository.save(entry);

            cacheHits.incrementAndGet();
            log.info("Component cache HIT: key={}, accessCount={}, format={}",
                    cacheKey, entry.getAccessCount(), entry.getFormat());

            return Optional.of(deserializeComponent(entry));
        }

        cacheMisses.incrementAndGet();
        log.debug("Component cache MISS: key={}", cacheKey);

        return Optional.empty();
    }

    /**
     * Retrieves a cached component export.
     *
     * @param cacheKey unique cache key
     * @return cached export if found and not expired
     */
    @Transactional
    public Optional<ComponentExport> getExport(String cacheKey) {
        if (!cacheEnabled) {
            return Optional.empty();
        }

        Optional<CachedComponent> cached = repository
                .findByCacheKeyAndExpiresAtAfter(cacheKey, LocalDateTime.now());

        if (cached.isPresent()) {
            CachedComponent entry = cached.get();
            entry.incrementAccessCount();
            repository.save(entry);

            cacheHits.incrementAndGet();
            log.info("Component export cache HIT: key={}, format={}",
                    cacheKey, entry.getFormat());

            return Optional.of(deserializeExport(entry));
        }

        cacheMisses.incrementAndGet();
        return Optional.empty();
    }

    /**
     * Stores a component in cache.
     *
     * @param url source URL
     * @param selector CSS selector
     * @param options extraction options
     * @param component component to cache
     * @return cache key, or null if not cached (too large, cache disabled, etc.)
     */
    @Transactional
    public String putComponent(String url, String selector, ExtractionOptions options, CompleteComponent component) {
        if (!cacheEnabled) {
            log.debug("Component cache disabled, skipping put");
            return null;
        }

        String cacheKey = generateCacheKey(url, selector, options, "JSON");
        long sizeBytes = calculateSize(component);

        if (sizeBytes > maxSizeBytes) {
            log.warn("Component too large to cache: size={}MB, max={}MB",
                    sizeBytes / 1024 / 1024, maxSizeBytes / 1024 / 1024);
            return null;
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(ttlSeconds);

        CachedComponent cached = new CachedComponent();
        cached.setCacheKey(cacheKey);
        cached.setUrl(url);
        cached.setSelector(selector);
        cached.setFormat("JSON");
        cached.setHtml(component.html());
        cached.setCss(component.css());
        cached.setJavascript(component.javascript());
        cached.setNamespace(component.namespace());
        cached.setMetadata(serializeMetadata(component.metadata()));
        cached.setStatistics(serializeStatistics(component.statistics()));
        cached.setSizeBytes(sizeBytes);
        cached.setExpiresAt(expiresAt);

        repository.save(cached);

        log.info("Component cached: key={}, size={}KB, ttl={}s",
                cacheKey, sizeBytes / 1024, ttlSeconds);

        return cacheKey;
    }

    /**
     * Stores a component export in cache.
     *
     * @param url source URL
     * @param selector CSS selector
     * @param options extraction options
     * @param format export format
     * @param export component export to cache
     * @return cache key, or null if not cached
     */
    @Transactional
    public String putExport(String url, String selector, ExtractionOptions options,
                           ExportFormat format, ComponentExport export) {
        if (!cacheEnabled) {
            return null;
        }

        String cacheKey = generateCacheKey(url, selector, options, format.name());
        long sizeBytes = calculateExportSize(export);

        if (sizeBytes > maxSizeBytes) {
            log.warn("Component export too large to cache: size={}MB, max={}MB",
                    sizeBytes / 1024 / 1024, maxSizeBytes / 1024 / 1024);
            return null;
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(ttlSeconds);

        CachedComponent cached = new CachedComponent();
        cached.setCacheKey(cacheKey);
        cached.setUrl(url);
        cached.setSelector(selector);
        cached.setFormat(format.name());
        cached.setExportData(serializeExportData(export));
        cached.setSizeBytes(sizeBytes);
        cached.setExpiresAt(expiresAt);

        repository.save(cached);

        log.info("Component export cached: key={}, format={}, size={}KB",
                cacheKey, format, sizeBytes / 1024);

        return cacheKey;
    }

    /**
     * Invalidates all cached components for a URL.
     *
     * @param url the URL to invalidate
     * @return number of entries deleted
     */
    @Transactional
    public int invalidateByUrl(String url) {
        log.info("Invalidating component cache for URL: {}", url);
        int deleted = repository.deleteByUrl(url);
        log.info("Invalidated {} component cache entries for URL: {}", deleted, url);
        return deleted;
    }

    /**
     * Clears all cached components.
     *
     * @return number of entries deleted
     */
    @Transactional
    public long invalidateAll() {
        log.warn("Invalidating entire component cache");
        long count = repository.count();
        repository.deleteAll();
        log.warn("Invalidated {} component cache entries", count);
        return count;
    }

    /**
     * Removes expired cached components.
     *
     * @return number of entries deleted
     */
    @Transactional
    public int invalidateExpired() {
        log.debug("Invalidating expired component cache entries");
        int deleted = repository.deleteExpired(LocalDateTime.now());
        log.info("Invalidated {} expired component cache entries", deleted);
        return deleted;
    }

    /**
     * Gets cache metrics.
     *
     * @return map of metric name to value
     */
    public Map<String, Object> getMetrics() {
        long totalEntries = repository.count();
        long totalAccess = repository.sumAllAccessCounts();
        long totalSizeBytes = repository.sumAllSizeBytes();
        long expiredCount = repository.countExpired(LocalDateTime.now());

        long totalRequests = cacheHits.get() + cacheMisses.get();
        double hitRate = totalRequests > 0
                ? (double) cacheHits.get() / totalRequests * 100
                : 0.0;

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalEntries", totalEntries);
        metrics.put("totalAccessCount", totalAccess);
        metrics.put("totalSizeMB", totalSizeBytes / 1024.0 / 1024.0);
        metrics.put("expiredCount", expiredCount);
        metrics.put("hitRate", hitRate);
        metrics.put("cacheHits", cacheHits.get());
        metrics.put("cacheMisses", cacheMisses.get());

        // Format-specific counts
        metrics.put("jsonCount", repository.countByFormat("JSON"));
        metrics.put("htmlCount", repository.countByFormat("HTML"));
        metrics.put("reactCount", repository.countByFormat("REACT"));
        metrics.put("vueCount", repository.countByFormat("VUE"));
        metrics.put("webComponentCount", repository.countByFormat("WEB_COMPONENT"));

        return metrics;
    }

    /**
     * Generates a cache key for component extraction.
     *
     * @param url source URL
     * @param selector CSS selector
     * @param options extraction options
     * @param format export format
     * @return MD5 cache key
     */
    public String generateCacheKey(String url, String selector, ExtractionOptions options, String format) {
        String input = url + "|" + selector + "|" + format + "|"
                + options.scopeCSS() + "|" + options.encapsulateJS() + "|"
                + options.inlineAssets() + "|" + options.maxAssetSize() + "|"
                + options.assetTypes() + "|" + options.customNamespace() + "|"
                + options.jsEncapsulationType();

        return hashString(input);
    }

    private String hashString(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to generate hash", e);
            return Integer.toHexString(input.hashCode());
        }
    }

    private long calculateSize(CompleteComponent component) {
        long size = 0;
        if (component.html() != null) size += component.html().length();
        if (component.css() != null) size += component.css().length();
        if (component.javascript() != null) size += component.javascript().length();
        if (component.namespace() != null) size += component.namespace().length();
        return size * 2; // UTF-16 approximation
    }

    private long calculateExportSize(ComponentExport export) {
        long size = 0;
        for (String content : export.files().values()) {
            if (content != null) size += content.length();
        }
        if (export.usageInstructions() != null) {
            size += export.usageInstructions().length();
        }
        return size * 2; // UTF-16 approximation
    }

    private CompleteComponent deserializeComponent(CachedComponent cached) {
        ComponentMetadata metadata = deserializeMetadata(cached.getMetadata());
        ExtractionStatistics statistics = deserializeStatistics(cached.getStatistics());

        return new CompleteComponent(
                cached.getHtml(),
                cached.getCss(),
                cached.getJavascript(),
                cached.getNamespace(),
                metadata,
                statistics
        );
    }

    private ComponentExport deserializeExport(CachedComponent cached) {
        try {
            Map<String, Object> data = objectMapper.readValue(cached.getExportData(), Map.class);
            ExportFormat format = ExportFormat.valueOf(cached.getFormat());

            @SuppressWarnings("unchecked")
            Map<String, String> files = (Map<String, String>) data.get("files");
            String mainFile = (String) data.get("mainFile");
            String usage = (String) data.get("usageInstructions");

            return new ComponentExport(format, files, mainFile, usage);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize export data", e);
            return null;
        }
    }

    private String serializeMetadata(ComponentMetadata metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize metadata", e);
            return "{}";
        }
    }

    private String serializeStatistics(ExtractionStatistics statistics) {
        try {
            return objectMapper.writeValueAsString(statistics);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize statistics", e);
            return "{}";
        }
    }

    private String serializeExportData(ComponentExport export) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("files", export.files());
            data.put("mainFile", export.mainFile());
            data.put("usageInstructions", export.usageInstructions());
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize export data", e);
            return "{}";
        }
    }

    private ComponentMetadata deserializeMetadata(String json) {
        try {
            return objectMapper.readValue(json, ComponentMetadata.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize metadata", e);
            return null;
        }
    }

    private ExtractionStatistics deserializeStatistics(String json) {
        try {
            return objectMapper.readValue(json, ExtractionStatistics.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize statistics", e);
            return null;
        }
    }
}
