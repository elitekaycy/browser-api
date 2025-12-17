package com.browserapi.component.repository;

import com.browserapi.component.entity.ComponentFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for accessing hosted component files.
 */
@Repository
public interface ComponentFileRepository extends JpaRepository<ComponentFile, Long> {

    /**
     * Finds a component file by its unique file ID.
     *
     * @param fileId the file ID
     * @return component file if found
     */
    Optional<ComponentFile> findByFileId(String fileId);

    /**
     * Finds a component file by file ID that hasn't expired.
     *
     * @param fileId the file ID
     * @param now current time
     * @return component file if found and not expired
     */
    Optional<ComponentFile> findByFileIdAndExpiresAtAfter(String fileId, LocalDateTime now);

    /**
     * Finds all component files for a source URL.
     *
     * @param url the source URL
     * @return list of component files
     */
    List<ComponentFile> findByUrl(String url);

    /**
     * Deletes all component files for a URL.
     *
     * @param url the source URL
     * @return number of entries deleted
     */
    @Modifying
    int deleteByUrl(String url);

    /**
     * Deletes all expired component files.
     *
     * @param now current time
     * @return number of entries deleted
     */
    @Modifying
    @Query("DELETE FROM ComponentFile c WHERE c.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    /**
     * Finds expired component files.
     * Used to delete physical files before removing database records.
     *
     * @param now current time
     * @return list of expired files
     */
    @Query("SELECT c FROM ComponentFile c WHERE c.expiresAt < :now")
    List<ComponentFile> findExpired(@Param("now") LocalDateTime now);

    /**
     * Counts component files by format.
     *
     * @param format the format (HTML, REACT, etc.)
     * @return count of files with that format
     */
    long countByFormat(String format);

    /**
     * Sums total view count across all component files.
     *
     * @return total view count
     */
    @Query("SELECT COALESCE(SUM(c.viewCount), 0) FROM ComponentFile c")
    long sumAllViewCounts();

    /**
     * Sums total file size in bytes across all component files.
     *
     * @return total size in bytes
     */
    @Query("SELECT COALESCE(SUM(c.fileSizeBytes), 0) FROM ComponentFile c")
    long sumAllFileSizes();

    /**
     * Finds most viewed component files.
     *
     * @return list of files ordered by view count descending
     */
    @Query("SELECT c FROM ComponentFile c ORDER BY c.viewCount DESC")
    List<ComponentFile> findMostViewed();

    /**
     * Counts files that are expired.
     *
     * @param now current time
     * @return count of expired files
     */
    @Query("SELECT COUNT(c) FROM ComponentFile c WHERE c.expiresAt < :now")
    long countExpired(@Param("now") LocalDateTime now);
}
