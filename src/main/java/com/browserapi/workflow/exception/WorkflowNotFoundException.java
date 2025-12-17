package com.browserapi.workflow.exception;

/**
 * Exception thrown when a workflow cannot be found by ID.
 */
public class WorkflowNotFoundException extends RuntimeException {

    private final String workflowId;

    public WorkflowNotFoundException(String workflowId) {
        super("Workflow not found: " + workflowId);
        this.workflowId = workflowId;
    }

    public String getWorkflowId() {
        return workflowId;
    }
}
