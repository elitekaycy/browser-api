package com.browserapi.cache.repository;

import com.browserapi.cache.entity.CachedResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for cached extraction responses.
 * Provides query methods for cache lookup, cleanup, and metrics.
 */
@Repository
public interface CachedResponseRepository extends JpaRepository<CachedResponse, UUID> {

    /**
     * Finds a non-expired cache entry by cache key.
     * Used for cache lookups during extraction requests.
     *
     * @param cacheKey the MD5 hash of request parameters
     * @param now current timestamp to filter expired entries
     * @return cached response if found and not expired
     */
    Optional<CachedResponse> findByCacheKeyAndExpiresAtAfter(String cacheKey, LocalDateTime now);

    /**
     * Deletes all expired cache entries.
     * Called by scheduled cleanup job.
     *
     * @param now current timestamp
     * @return number of deleted entries
     */
    @Modifying
    @Query("DELETE FROM CachedResponse WHERE expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    /**
     * Counts cache entries by extraction type.
     * Used for cache metrics and monitoring.
     *
     * @param extractionType the type (HTML, CSS, JSON)
     * @return count of cached entries
     */
    long countByExtractionType(String extractionType);

    /**
     * Counts total cache hits across all entries.
     * Used for cache effectiveness metrics.
     *
     * @return sum of all hits
     */
    @Query("SELECT COALESCE(SUM(c.hits), 0) FROM CachedResponse c")
    long sumAllHits();

    /**
     * Finds all cache entries that will expire soon.
     * Useful for monitoring and debugging.
     *
     * @param threshold expiration threshold
     * @return count of entries expiring soon
     */
    long countByExpiresAtBefore(LocalDateTime threshold);

    /**
     * Deletes all cache entries for a specific URL.
     * Used for cache invalidation when content changes.
     *
     * @param url the URL to invalidate
     * @return number of deleted entries
     */
    @Modifying
    int deleteByUrl(String url);
}
