package com.browserapi.workflow.model;

import com.browserapi.action.model.Action;

import java.util.List;

/**
 * Request model for creating a new workflow.
 */
public record WorkflowCreateRequest(
        String name,
        String description,
        String url,
        List<Action> actions,
        List<String> tags,
        String createdBy
) {
    /**
     * Create a minimal workflow request.
     */
    public WorkflowCreateRequest(String name, String url, List<Action> actions) {
        this(name, null, url, actions, List.of(), null);
    }
}
