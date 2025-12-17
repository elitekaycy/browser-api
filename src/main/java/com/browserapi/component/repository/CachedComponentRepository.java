package com.browserapi.component.repository;

import com.browserapi.component.entity.CachedComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for accessing cached component data.
 */
@Repository
public interface CachedComponentRepository extends JpaRepository<CachedComponent, Long> {

    /**
     * Finds a cached component by cache key that hasn't expired.
     *
     * @param cacheKey cache key
     * @param now current time
     * @return cached component if found and not expired
     */
    Optional<CachedComponent> findByCacheKeyAndExpiresAtAfter(String cacheKey, LocalDateTime now);

    /**
     * Finds all cached components for a URL.
     *
     * @param url the URL
     * @return list of cached components
     */
    List<CachedComponent> findByUrl(String url);

    /**
     * Deletes all cached components for a URL.
     *
     * @param url the URL
     * @return number of entries deleted
     */
    @Modifying
    int deleteByUrl(String url);

    /**
     * Deletes all expired cached components.
     *
     * @param now current time
     * @return number of entries deleted
     */
    @Modifying
    @Query("DELETE FROM CachedComponent c WHERE c.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    /**
     * Counts cached components by format.
     *
     * @param format the format (JSON, HTML, REACT, etc.)
     * @return count of components with that format
     */
    long countByFormat(String format);

    /**
     * Sums total access count across all cached components.
     *
     * @return total access count
     */
    @Query("SELECT COALESCE(SUM(c.accessCount), 0) FROM CachedComponent c")
    long sumAllAccessCounts();

    /**
     * Sums total size in bytes across all cached components.
     *
     * @return total size in bytes
     */
    @Query("SELECT COALESCE(SUM(c.sizeBytes), 0) FROM CachedComponent c")
    long sumAllSizeBytes();

    /**
     * Finds cached components exceeding a size limit.
     *
     * @param maxSizeBytes maximum size in bytes
     * @return list of oversized components
     */
    @Query("SELECT c FROM CachedComponent c WHERE c.sizeBytes > :maxSizeBytes")
    List<CachedComponent> findOversizedComponents(@Param("maxSizeBytes") long maxSizeBytes);

    /**
     * Finds least recently accessed components.
     * Useful for eviction strategies.
     *
     * @param limit number of results
     * @return list of components ordered by last accessed
     */
    @Query("SELECT c FROM CachedComponent c ORDER BY c.lastAccessedAt ASC NULLS FIRST")
    List<CachedComponent> findLeastRecentlyAccessed(@Param("limit") int limit);

    /**
     * Counts components that are expired.
     *
     * @param now current time
     * @return count of expired components
     */
    @Query("SELECT COUNT(c) FROM CachedComponent c WHERE c.expiresAt < :now")
    long countExpired(@Param("now") LocalDateTime now);
}
