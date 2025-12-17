package com.browserapi.workflow.model;

import com.browserapi.action.model.ActionResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Result of executing a workflow.
 * Contains action results, statistics, and final state.
 */
public record WorkflowExecutionResult(
        String workflowId,
        String workflowName,
        boolean success,
        List<ActionResult> actionResults,
        long executionTimeMs,
        String finalUrl,
        LocalDateTime executedAt
) {
    /**
     * Count of successful actions.
     */
    public int getSuccessCount() {
        return (int) actionResults.stream()
                .filter(ActionResult::success)
                .count();
    }

    /**
     * Count of failed actions.
     */
    public int getFailureCount() {
        return actionResults.size() - getSuccessCount();
    }

    /**
     * Total number of actions executed.
     */
    public int getTotalActions() {
        return actionResults.size();
    }
}
