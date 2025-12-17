package com.browserapi.workflow.entity;

import com.browserapi.action.model.Action;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Persistent entity representing a reusable browser workflow.
 * A workflow is a named sequence of actions that can be executed multiple times.
 */
@Entity
@Table(name = "workflows", indexes = {
        @Index(name = "idx_workflows_tags", columnList = "tags"),
        @Index(name = "idx_workflows_created_at", columnList = "created_at"),
        @Index(name = "idx_workflows_last_executed_at", columnList = "last_executed_at")
})
public class Workflow {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 2048)
    private String url;

    /**
     * Actions stored as JSON string.
     * Use getActions()/setActions() to work with Action objects.
     */
    @Column(nullable = false, columnDefinition = "TEXT", name = "actions_json")
    private String actionsJson;

    /**
     * Comma-separated tags for categorization.
     * Use getTagList()/setTagList() to work with tag lists.
     */
    @Column(length = 1000)
    private String tags;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    // Execution statistics
    @Column(name = "total_executions")
    private Integer totalExecutions = 0;

    @Column(name = "successful_executions")
    private Integer successfulExecutions = 0;

    @Column(name = "failed_executions")
    private Integer failedExecutions = 0;

    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    @Column(name = "average_execution_ms")
    private Long averageExecutionMs;

    @Transient
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * JPA lifecycle callback - set ID and timestamps on create.
     */
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
        if (totalExecutions == null) {
            totalExecutions = 0;
        }
        if (successfulExecutions == null) {
            successfulExecutions = 0;
        }
        if (failedExecutions == null) {
            failedExecutions = 0;
        }
    }

    /**
     * JPA lifecycle callback - update timestamp on update.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Records an execution of this workflow and updates statistics.
     *
     * @param success whether the execution was successful
     * @param executionMs execution time in milliseconds
     */
    public void recordExecution(boolean success, long executionMs) {
        totalExecutions++;
        if (success) {
            successfulExecutions++;
        } else {
            failedExecutions++;
        }
        lastExecutedAt = LocalDateTime.now();

        // Update rolling average
        if (averageExecutionMs == null) {
            averageExecutionMs = executionMs;
        } else {
            // Simple moving average
            averageExecutionMs = (averageExecutionMs + executionMs) / 2;
        }
    }

    /**
     * Calculates the success rate as a percentage.
     *
     * @return success rate (0-100)
     */
    public double getSuccessRate() {
        if (totalExecutions == null || totalExecutions == 0) {
            return 0.0;
        }
        return (double) successfulExecutions / totalExecutions * 100.0;
    }

    /**
     * Gets tags as a list.
     *
     * @return list of tags
     */
    public List<String> getTagList() {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(tags.split(","));
    }

    /**
     * Sets tags from a list.
     *
     * @param tagList list of tags
     */
    public void setTagList(List<String> tagList) {
        if (tagList == null || tagList.isEmpty()) {
            this.tags = null;
        } else {
            this.tags = String.join(",", tagList);
        }
    }

    /**
     * Deserializes actions from JSON.
     *
     * @return list of actions
     * @throws JsonProcessingException if deserialization fails
     */
    public List<Action> getActions() throws JsonProcessingException {
        if (actionsJson == null || actionsJson.isEmpty()) {
            return List.of();
        }
        return objectMapper.readValue(actionsJson, new TypeReference<List<Action>>() {});
    }

    /**
     * Serializes actions to JSON.
     *
     * @param actions list of actions
     * @throws JsonProcessingException if serialization fails
     */
    public void setActions(List<Action> actions) throws JsonProcessingException {
        if (actions == null || actions.isEmpty()) {
            this.actionsJson = "[]";
        } else {
            this.actionsJson = objectMapper.writeValueAsString(actions);
        }
    }

    /**
     * Gets the raw JSON string of actions (for JPA).
     *
     * @return actions JSON string
     */
    public String getActionsJson() {
        return actionsJson;
    }

    /**
     * Sets the raw JSON string of actions (for JPA).
     *
     * @param actionsJson actions JSON string
     */
    public void setActionsJson(String actionsJson) {
        this.actionsJson = actionsJson;
    }

    /**
     * Checks if this workflow has been executed at least once.
     *
     * @return true if executed
     */
    public boolean hasBeenExecuted() {
        return totalExecutions != null && totalExecutions > 0;
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Integer getTotalExecutions() {
        return totalExecutions;
    }

    public void setTotalExecutions(Integer totalExecutions) {
        this.totalExecutions = totalExecutions;
    }

    public Integer getSuccessfulExecutions() {
        return successfulExecutions;
    }

    public void setSuccessfulExecutions(Integer successfulExecutions) {
        this.successfulExecutions = successfulExecutions;
    }

    public Integer getFailedExecutions() {
        return failedExecutions;
    }

    public void setFailedExecutions(Integer failedExecutions) {
        this.failedExecutions = failedExecutions;
    }

    public LocalDateTime getLastExecutedAt() {
        return lastExecutedAt;
    }

    public void setLastExecutedAt(LocalDateTime lastExecutedAt) {
        this.lastExecutedAt = lastExecutedAt;
    }

    public Long getAverageExecutionMs() {
        return averageExecutionMs;
    }

    public void setAverageExecutionMs(Long averageExecutionMs) {
        this.averageExecutionMs = averageExecutionMs;
    }
}
