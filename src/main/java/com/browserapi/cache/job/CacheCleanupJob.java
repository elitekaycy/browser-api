package com.browserapi.cache.job;

import com.browserapi.cache.repository.CachedResponseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduled job to cleanup expired cache entries.
 * Runs hourly to remove entries past their TTL.
 */
@Component
public class CacheCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(CacheCleanupJob.class);

    private final CachedResponseRepository repository;

    public CacheCleanupJob(CachedResponseRepository repository) {
        this.repository = repository;
    }

    /**
     * Deletes all expired cache entries.
     * Runs every hour at the top of the hour (0 minutes, 0 seconds).
     * <p>
     * Cron format: second minute hour day month weekday
     * "0 0 * * * *" = At 0 seconds, 0 minutes, every hour
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredCache() {
        log.debug("Starting cache cleanup job");

        try {
            LocalDateTime now = LocalDateTime.now();
            int deletedCount = repository.deleteExpired(now);

            if (deletedCount > 0) {
                log.info("Cache cleanup completed: deleted {} expired entries", deletedCount);
            } else {
                log.debug("Cache cleanup completed: no expired entries found");
            }

            long remainingCount = repository.count();
            log.debug("Cache entries remaining: {}", remainingCount);

        } catch (Exception e) {
            log.error("Cache cleanup job failed", e);
        }
    }

    /**
     * Logs cache statistics.
     * Runs every 30 minutes for monitoring.
     */
    @Scheduled(cron = "0 */30 * * * *")
    public void logCacheStats() {
        try {
            long totalEntries = repository.count();
            long totalHits = repository.sumAllHits();

            long htmlCount = repository.countByExtractionType("HTML");
            long cssCount = repository.countByExtractionType("CSS");
            long jsonCount = repository.countByExtractionType("JSON");

            log.info("Cache stats: total={}, hits={}, HTML={}, CSS={}, JSON={}",
                    totalEntries, totalHits, htmlCount, cssCount, jsonCount);

            LocalDateTime soonThreshold = LocalDateTime.now().plusMinutes(30);
            long expiringSoon = repository.countByExpiresAtBefore(soonThreshold);
            if (expiringSoon > 0) {
                log.debug("Cache entries expiring in next 30 minutes: {}", expiringSoon);
            }

        } catch (Exception e) {
            log.error("Failed to log cache stats", e);
        }
    }
}
