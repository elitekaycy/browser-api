package com.browserapi.workflow.model;

import com.browserapi.action.model.Action;

import java.util.List;

/**
 * Request model for updating an existing workflow.
 * All fields are optional - only provided fields will be updated.
 */
public record WorkflowUpdateRequest(
        String name,
        String description,
        String url,
        List<Action> actions,
        List<String> tags
) {
}
