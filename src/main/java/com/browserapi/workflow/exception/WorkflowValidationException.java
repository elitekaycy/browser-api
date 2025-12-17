package com.browserapi.workflow.exception;

import java.util.ArrayList;
import java.util.List;

/**
 * Exception thrown when workflow validation fails.
 */
public class WorkflowValidationException extends RuntimeException {

    private final List<String> validationErrors;

    public WorkflowValidationException(String message) {
        super(message);
        this.validationErrors = List.of(message);
    }

    public WorkflowValidationException(List<String> validationErrors) {
        super("Workflow validation failed: " + String.join(", ", validationErrors));
        this.validationErrors = new ArrayList<>(validationErrors);
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
