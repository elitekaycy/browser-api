package com.browserapi.workflow.model;

/**
 * Aggregated statistics across all workflows.
 */
public record WorkflowStatistics(
        long totalWorkflows,
        Long totalExecutions,
        Long successfulExecutions,
        Long failedExecutions,
        Double averageExecutionTimeMs
) {
    /**
     * Calculate global success rate across all workflows.
     */
    public double getGlobalSuccessRate() {
        if (totalExecutions == null || totalExecutions == 0) {
            return 0.0;
        }
        if (successfulExecutions == null) {
            return 0.0;
        }
        return (double) successfulExecutions / totalExecutions * 100.0;
    }

    /**
     * Get average execution time rounded to integer.
     */
    public long getAverageExecutionTimeMsRounded() {
        if (averageExecutionTimeMs == null) {
            return 0;
        }
        return Math.round(averageExecutionTimeMs);
    }
}
