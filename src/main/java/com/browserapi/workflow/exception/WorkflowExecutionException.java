package com.browserapi.workflow.exception;

/**
 * Exception thrown when workflow execution fails.
 */
public class WorkflowExecutionException extends RuntimeException {

    private final String workflowId;

    public WorkflowExecutionException(String message) {
        super(message);
        this.workflowId = null;
    }

    public WorkflowExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.workflowId = null;
    }

    public WorkflowExecutionException(String workflowId, String message, Throwable cause) {
        super(message, cause);
        this.workflowId = workflowId;
    }

    public String getWorkflowId() {
        return workflowId;
    }
}
