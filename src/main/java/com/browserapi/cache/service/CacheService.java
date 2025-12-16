package com.browserapi.cache.service;

import com.browserapi.cache.config.CacheConfig;
import com.browserapi.cache.dto.CacheMetrics;
import com.browserapi.cache.entity.CachedResponse;
import com.browserapi.cache.repository.CachedResponseRepository;
import com.browserapi.cache.util.CacheKeyGenerator;
import com.browserapi.extraction.ExtractionType;
import com.browserapi.extraction.dto.ExtractionRequest;
import com.browserapi.extraction.dto.ExtractionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for cache management operations.
 * Provides get/put/invalidate operations with TTL-based expiration.
 * <p>
 * Caching strategy:
 * <ul>
 *   <li>Cache key: MD5 hash of (url + type + selector + options)</li>
 *   <li>TTL: Configurable per extraction type</li>
 *   <li>Metrics: Track hits, misses, and hit rate</li>
 * </ul>
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final CachedResponseRepository repository;
    private final CacheConfig cacheConfig;

    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    public CacheService(CachedResponseRepository repository, CacheConfig cacheConfig) {
        this.repository = repository;
        this.cacheConfig = cacheConfig;
    }

    /**
     * Attempts to retrieve a cached response for the given request.
     * Returns empty if cache disabled, not found, or expired.
     *
     * @param request extraction request
     * @return cached response if found and valid
     */
    @Transactional
    public Optional<ExtractionResponse> get(ExtractionRequest request) {
        if (!cacheConfig.isEnabled()) {
            log.debug("Cache disabled, skipping lookup");
            return Optional.empty();
        }

        String cacheKey = CacheKeyGenerator.generate(request);

        log.debug("Cache lookup: key={}, url={}, type={}, selector={}",
                cacheKey, request.url(), request.type(), request.selector());

        Optional<CachedResponse> cached = repository
                .findByCacheKeyAndExpiresAtAfter(cacheKey, LocalDateTime.now());

        if (cached.isPresent()) {
            CachedResponse entry = cached.get();
            entry.incrementHits();
            repository.save(entry);

            cacheHits.incrementAndGet();

            log.info("Cache HIT: key={}, url={}, type={}, hits={}",
                    cacheKey, request.url(), request.type(), entry.getHits());

            return Optional.of(deserializeResponse(entry));
        }

        cacheMisses.incrementAndGet();

        log.debug("Cache MISS: key={}, url={}, type={}",
                cacheKey, request.url(), request.type());

        return Optional.empty();
    }

    /**
     * Stores an extraction response in the cache.
     * TTL is determined by extraction type configuration.
     *
     * @param request extraction request
     * @param response extraction response to cache
     */
    @Transactional
    public void put(ExtractionRequest request, ExtractionResponse response) {
        if (!cacheConfig.isEnabled()) {
            log.debug("Cache disabled, skipping put");
            return;
        }

        String cacheKey = CacheKeyGenerator.generate(request);
        int ttlSeconds = cacheConfig.getTtlForType(request.type().name());
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(ttlSeconds);

        CachedResponse cached = new CachedResponse(
                cacheKey,
                request.url(),
                request.type().name(),
                request.selector(),
                response.data(),
                serializeMetadata(response.metadata()),
                expiresAt
        );

        repository.save(cached);

        log.info("Cache PUT: key={}, url={}, type={}, ttl={}s, expiresAt={}",
                cacheKey, request.url(), request.type(), ttlSeconds, expiresAt);
    }

    /**
     * Invalidates all cache entries for a specific URL.
     * Useful when content is known to have changed.
     *
     * @param url the URL to invalidate
     * @return number of entries deleted
     */
    @Transactional
    public int invalidateByUrl(String url) {
        log.info("Invalidating cache for URL: {}", url);
        int deleted = repository.deleteByUrl(url);
        log.info("Invalidated {} cache entries for URL: {}", deleted, url);
        return deleted;
    }

    /**
     * Invalidates all cache entries.
     * Nuclear option for cache clearing.
     *
     * @return number of entries deleted
     */
    @Transactional
    public long invalidateAll() {
        log.warn("Invalidating entire cache");
        long count = repository.count();
        repository.deleteAll();
        log.warn("Invalidated {} cache entries", count);
        return count;
    }

    /**
     * Manually triggers cleanup of expired entries.
     * Usually called by scheduled job.
     *
     * @return number of entries deleted
     */
    @Transactional
    public int invalidateExpired() {
        log.debug("Manually invalidating expired cache entries");
        int deleted = repository.deleteExpired(LocalDateTime.now());
        log.info("Invalidated {} expired cache entries", deleted);
        return deleted;
    }

    /**
     * Gets cache effectiveness metrics.
     *
     * @return cache metrics
     */
    public CacheMetrics getMetrics() {
        long totalEntries = repository.count();
        long totalHits = repository.sumAllHits();

        long htmlCount = repository.countByExtractionType(ExtractionType.HTML.name());
        long cssCount = repository.countByExtractionType(ExtractionType.CSS.name());
        long jsonCount = repository.countByExtractionType(ExtractionType.JSON.name());

        long totalRequests = cacheHits.get() + cacheMisses.get();
        double hitRate = totalRequests > 0
                ? (double) cacheHits.get() / totalRequests * 100
                : 0.0;

        return new CacheMetrics(
                totalEntries,
                totalHits,
                hitRate,
                htmlCount,
                cssCount,
                jsonCount
        );
    }

    private ExtractionResponse deserializeResponse(CachedResponse cached) {
        Map<String, Object> metadata = deserializeMetadata(cached.getMetadata());

        return new ExtractionResponse(
                cached.getData(),
                ExtractionType.valueOf(cached.getExtractionType()),
                cached.getSelector(),
                0L,
                metadata
        );
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }

        StringBuilder json = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            json.append("  \"").append(entry.getKey()).append("\": ");

            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Boolean || value instanceof Number) {
                json.append(value);
            } else {
                json.append("\"").append(escapeJson(value.toString())).append("\"");
            }

            if (i < metadata.size() - 1) {
                json.append(",");
            }
            json.append("\n");
            i++;
        }
        json.append("}");

        return json.toString();
    }

    private Map<String, Object> deserializeMetadata(String json) {
        Map<String, Object> metadata = new HashMap<>();

        if (json == null || json.isBlank() || json.equals("{}")) {
            return metadata;
        }

        String content = json.trim();
        if (content.startsWith("{")) {
            content = content.substring(1);
        }
        if (content.endsWith("}")) {
            content = content.substring(0, content.length() - 1);
        }

        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.equals("{") || line.equals("}")) {
                continue;
            }

            if (line.endsWith(",")) {
                line = line.substring(0, line.length() - 1);
            }

            int colonIndex = line.indexOf(":");
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();

                key = key.replaceAll("\"", "");

                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                    metadata.put(key, unescapeJson(value));
                } else if (value.equals("true") || value.equals("false")) {
                    metadata.put(key, Boolean.parseBoolean(value));
                } else {
                    try {
                        if (value.contains(".")) {
                            metadata.put(key, Double.parseDouble(value));
                        } else {
                            metadata.put(key, Long.parseLong(value));
                        }
                    } catch (NumberFormatException e) {
                        metadata.put(key, value);
                    }
                }
            }
        }

        return metadata;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}
