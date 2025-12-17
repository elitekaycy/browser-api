package com.browserapi.component.job;

import com.browserapi.component.service.ComponentHostingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job to clean up expired hosted component files.
 * Runs every hour to delete both physical files and database records.
 */
@Component
public class ComponentFileCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ComponentFileCleanupJob.class);

    private final ComponentHostingService hostingService;

    public ComponentFileCleanupJob(ComponentHostingService hostingService) {
        this.hostingService = hostingService;
    }

    /**
     * Runs every hour (at the top of the hour).
     * Deletes expired component files from disk and database.
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour at :00
    public void cleanupExpiredFiles() {
        log.info("Starting component file cleanup job");

        try {
            int deletedCount = hostingService.cleanupExpiredFiles();
            log.info("Component file cleanup completed: deleted {} files", deletedCount);

        } catch (Exception e) {
            log.error("Component file cleanup job failed", e);
        }
    }
}
