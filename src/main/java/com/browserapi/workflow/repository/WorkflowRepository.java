package com.browserapi.workflow.repository;

import com.browserapi.workflow.entity.Workflow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for CRUD operations on Workflow entities.
 * Provides custom queries for searching, filtering, and analytics.
 */
@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, String> {

    /**
     * Find workflows by name (case-insensitive partial match).
     *
     * @param name name fragment to search for
     * @return list of matching workflows
     */
    List<Workflow> findByNameContainingIgnoreCase(String name);

    /**
     * Find workflows by name with pagination.
     *
     * @param name name fragment to search for
     * @param pageable pagination parameters
     * @return page of matching workflows
     */
    Page<Workflow> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * Find workflows containing a specific tag.
     *
     * @param tag tag to search for
     * @return list of workflows with that tag
     */
    @Query("SELECT w FROM Workflow w WHERE w.tags LIKE %:tag%")
    List<Workflow> findByTag(@Param("tag") String tag);

    /**
     * Find workflows containing a specific tag with pagination.
     *
     * @param tag tag to search for
     * @param pageable pagination parameters
     * @return page of workflows with that tag
     */
    @Query("SELECT w FROM Workflow w WHERE w.tags LIKE %:tag%")
    Page<Workflow> findByTag(@Param("tag") String tag, Pageable pageable);

    /**
     * Find workflows by URL (exact match).
     *
     * @param url URL to search for
     * @return list of workflows for that URL
     */
    List<Workflow> findByUrl(String url);

    /**
     * Find workflows by URL (partial match).
     *
     * @param url URL fragment to search for
     * @return list of workflows with matching URL
     */
    List<Workflow> findByUrlContaining(String url);

    /**
     * Find workflows created by a specific user.
     *
     * @param createdBy username or user ID
     * @return list of workflows created by that user
     */
    List<Workflow> findByCreatedBy(String createdBy);

    /**
     * Find most executed workflows.
     *
     * @return top 10 most executed workflows
     */
    List<Workflow> findTop10ByOrderByTotalExecutionsDesc();

    /**
     * Find most successful workflows (by success rate).
     * Only includes workflows that have been executed at least once.
     *
     * @param pageable pagination parameters
     * @return page of workflows ordered by success rate
     */
    @Query("SELECT w FROM Workflow w WHERE w.totalExecutions > 0 " +
           "ORDER BY (CAST(w.successfulExecutions AS double) / w.totalExecutions) DESC")
    Page<Workflow> findMostSuccessful(Pageable pageable);

    /**
     * Find recently executed workflows.
     *
     * @return top 10 recently executed workflows
     */
    List<Workflow> findTop10ByLastExecutedAtIsNotNullOrderByLastExecutedAtDesc();

    /**
     * Find recently created workflows.
     *
     * @return top 10 recently created workflows
     */
    List<Workflow> findTop10ByOrderByCreatedAtDesc();

    /**
     * Find workflows that have never been executed.
     *
     * @return list of workflows with zero executions
     */
    @Query("SELECT w FROM Workflow w WHERE w.totalExecutions = 0 OR w.totalExecutions IS NULL")
    List<Workflow> findNeverExecuted();

    /**
     * Find workflows with low success rate (below threshold).
     *
     * @param minExecutions minimum number of executions required
     * @param maxSuccessRate maximum success rate threshold (0-1)
     * @return list of workflows with low success rate
     */
    @Query("SELECT w FROM Workflow w WHERE w.totalExecutions >= :minExecutions " +
           "AND (CAST(w.successfulExecutions AS double) / w.totalExecutions) < :maxSuccessRate")
    List<Workflow> findLowSuccessRate(@Param("minExecutions") int minExecutions,
                                      @Param("maxSuccessRate") double maxSuccessRate);

    // Statistics queries

    /**
     * Count all workflows.
     *
     * @return total number of workflows
     */
    @Query("SELECT COUNT(w) FROM Workflow w")
    long countAll();

    /**
     * Sum total executions across all workflows.
     *
     * @return total number of executions
     */
    @Query("SELECT SUM(w.totalExecutions) FROM Workflow w")
    Long sumTotalExecutions();

    /**
     * Sum successful executions across all workflows.
     *
     * @return total number of successful executions
     */
    @Query("SELECT SUM(w.successfulExecutions) FROM Workflow w")
    Long sumSuccessfulExecutions();

    /**
     * Sum failed executions across all workflows.
     *
     * @return total number of failed executions
     */
    @Query("SELECT SUM(w.failedExecutions) FROM Workflow w")
    Long sumFailedExecutions();

    /**
     * Calculate average execution time across all workflows.
     *
     * @return average execution time in milliseconds
     */
    @Query("SELECT AVG(w.averageExecutionMs) FROM Workflow w WHERE w.averageExecutionMs IS NOT NULL")
    Double getAverageExecutionTime();

    /**
     * Get count of workflows by tag.
     *
     * @return list of [tag, count] pairs
     */
    @Query("SELECT w.tags, COUNT(w) FROM Workflow w WHERE w.tags IS NOT NULL GROUP BY w.tags")
    List<Object[]> countByTags();

    /**
     * Find workflows with name or description matching search term.
     *
     * @param searchTerm term to search for
     * @param pageable pagination parameters
     * @return page of matching workflows
     */
    @Query("SELECT w FROM Workflow w WHERE " +
           "LOWER(w.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(w.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Workflow> search(@Param("searchTerm") String searchTerm, Pageable pageable);
}
