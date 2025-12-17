package com.browserapi.workflow.service;

import com.browserapi.workflow.entity.Workflow;
import com.browserapi.workflow.exception.WorkflowNotFoundException;
import com.browserapi.workflow.exception.WorkflowValidationException;
import com.browserapi.workflow.model.WorkflowCreateRequest;
import com.browserapi.workflow.model.WorkflowStatistics;
import com.browserapi.workflow.model.WorkflowUpdateRequest;
import com.browserapi.workflow.repository.WorkflowRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing workflows (CRUD operations, validation, search).
 */
@Service
@Transactional
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_DESCRIPTION_LENGTH = 5000;
    private static final int MAX_ACTIONS_PER_WORKFLOW = 50;

    private final WorkflowRepository repository;

    public WorkflowService(WorkflowRepository repository) {
        this.repository = repository;
    }

    /**
     * Create a new workflow.
     *
     * @param request workflow creation request
     * @return created workflow
     * @throws WorkflowValidationException if validation fails
     */
    public Workflow create(WorkflowCreateRequest request) {
        log.info("Creating workflow: name={}", request.name());

        // Validate request
        validateWorkflowRequest(request);

        try {
            // Create workflow entity
            Workflow workflow = new Workflow();
            workflow.setName(request.name());
            workflow.setDescription(request.description());
            workflow.setUrl(request.url());
            workflow.setActions(request.actions());
            workflow.setTagList(request.tags() != null ? request.tags() : List.of());
            workflow.setCreatedBy(request.createdBy());

            // Save
            Workflow saved = repository.save(workflow);
            log.info("Workflow created: id={}, name={}", saved.getId(), saved.getName());

            return saved;

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize workflow actions: name={}", request.name(), e);
            throw new WorkflowValidationException("Failed to serialize actions: " + e.getMessage());
        }
    }

    /**
     * Update an existing workflow.
     *
     * @param id workflow ID
     * @param request update request
     * @return updated workflow
     * @throws WorkflowNotFoundException if workflow not found
     * @throws WorkflowValidationException if validation fails
     */
    public Workflow update(String id, WorkflowUpdateRequest request) {
        log.info("Updating workflow: id={}", id);

        Workflow workflow = repository.findById(id)
                .orElseThrow(() -> new WorkflowNotFoundException(id));

        try {
            // Update fields (only if provided)
            if (request.name() != null) {
                validateName(request.name());
                workflow.setName(request.name());
            }

            if (request.description() != null) {
                validateDescription(request.description());
                workflow.setDescription(request.description());
            }

            if (request.url() != null) {
                validateUrl(request.url());
                workflow.setUrl(request.url());
            }

            if (request.actions() != null) {
                validateActions(request.actions().size());
                workflow.setActions(request.actions());
            }

            if (request.tags() != null) {
                workflow.setTagList(request.tags());
            }

            Workflow updated = repository.save(workflow);
            log.info("Workflow updated: id={}, name={}", updated.getId(), updated.getName());

            return updated;

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize workflow actions: id={}", id, e);
            throw new WorkflowValidationException("Failed to serialize actions: " + e.getMessage());
        }
    }

    /**
     * Delete a workflow by ID.
     *
     * @param id workflow ID
     * @throws WorkflowNotFoundException if workflow not found
     */
    public void delete(String id) {
        log.info("Deleting workflow: id={}", id);

        if (!repository.existsById(id)) {
            throw new WorkflowNotFoundException(id);
        }

        repository.deleteById(id);
        log.info("Workflow deleted: id={}", id);
    }

    /**
     * Get a workflow by ID.
     *
     * @param id workflow ID
     * @return workflow
     * @throws WorkflowNotFoundException if workflow not found
     */
    @Transactional(readOnly = true)
    public Workflow getById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new WorkflowNotFoundException(id));
    }

    /**
     * Get all workflows.
     *
     * @return list of all workflows
     */
    @Transactional(readOnly = true)
    public List<Workflow> getAll() {
        return repository.findAll();
    }

    /**
     * Get workflows with pagination.
     *
     * @param pageable pagination parameters
     * @return page of workflows
     */
    @Transactional(readOnly = true)
    public Page<Workflow> getAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    /**
     * Search workflows by name (case-insensitive).
     *
     * @param name name fragment
     * @return list of matching workflows
     */
    @Transactional(readOnly = true)
    public List<Workflow> searchByName(String name) {
        return repository.findByNameContainingIgnoreCase(name);
    }

    /**
     * Search workflows by name with pagination.
     *
     * @param name name fragment
     * @param pageable pagination parameters
     * @return page of matching workflows
     */
    @Transactional(readOnly = true)
    public Page<Workflow> searchByName(String name, Pageable pageable) {
        return repository.findByNameContainingIgnoreCase(name, pageable);
    }

    /**
     * Find workflows by tag.
     *
     * @param tag tag to search for
     * @return list of workflows with that tag
     */
    @Transactional(readOnly = true)
    public List<Workflow> findByTag(String tag) {
        return repository.findByTag(tag);
    }

    /**
     * Find workflows by tag with pagination.
     *
     * @param tag tag to search for
     * @param pageable pagination parameters
     * @return page of workflows with that tag
     */
    @Transactional(readOnly = true)
    public Page<Workflow> findByTag(String tag, Pageable pageable) {
        return repository.findByTag(tag, pageable);
    }

    /**
     * Find workflows by creator.
     *
     * @param createdBy creator username
     * @return list of workflows by that creator
     */
    @Transactional(readOnly = true)
    public List<Workflow> findByCreator(String createdBy) {
        return repository.findByCreatedBy(createdBy);
    }

    /**
     * Get most executed workflows.
     *
     * @return top 10 most executed workflows
     */
    @Transactional(readOnly = true)
    public List<Workflow> getMostExecuted() {
        return repository.findTop10ByOrderByTotalExecutionsDesc();
    }

    /**
     * Get most successful workflows (by success rate).
     *
     * @param pageable pagination parameters
     * @return page of workflows ordered by success rate
     */
    @Transactional(readOnly = true)
    public Page<Workflow> getMostSuccessful(Pageable pageable) {
        return repository.findMostSuccessful(pageable);
    }

    /**
     * Get recently executed workflows.
     *
     * @return top 10 recently executed workflows
     */
    @Transactional(readOnly = true)
    public List<Workflow> getRecentlyExecuted() {
        return repository.findTop10ByLastExecutedAtIsNotNullOrderByLastExecutedAtDesc();
    }

    /**
     * Get recently created workflows.
     *
     * @return top 10 recently created workflows
     */
    @Transactional(readOnly = true)
    public List<Workflow> getRecentlyCreated() {
        return repository.findTop10ByOrderByCreatedAtDesc();
    }

    /**
     * Get workflows that have never been executed.
     *
     * @return list of workflows with zero executions
     */
    @Transactional(readOnly = true)
    public List<Workflow> getNeverExecuted() {
        return repository.findNeverExecuted();
    }

    /**
     * Get aggregated statistics across all workflows.
     *
     * @return workflow statistics
     */
    @Transactional(readOnly = true)
    public WorkflowStatistics getStatistics() {
        return new WorkflowStatistics(
                repository.countAll(),
                repository.sumTotalExecutions(),
                repository.sumSuccessfulExecutions(),
                repository.sumFailedExecutions(),
                repository.getAverageExecutionTime()
        );
    }

    // Validation methods

    private void validateWorkflowRequest(WorkflowCreateRequest request) {
        List<String> errors = new ArrayList<>();

        if (request.name() == null || request.name().isBlank()) {
            errors.add("Workflow name is required");
        } else {
            validateName(request.name(), errors);
        }

        if (request.url() == null || request.url().isBlank()) {
            errors.add("Workflow URL is required");
        } else {
            validateUrl(request.url(), errors);
        }

        if (request.actions() == null || request.actions().isEmpty()) {
            errors.add("Workflow must have at least one action");
        } else {
            validateActions(request.actions().size(), errors);
        }

        if (request.description() != null) {
            validateDescription(request.description(), errors);
        }

        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    private void validateName(String name) {
        List<String> errors = new ArrayList<>();
        validateName(name, errors);
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    private void validateName(String name, List<String> errors) {
        if (name.length() > MAX_NAME_LENGTH) {
            errors.add("Workflow name too long (max " + MAX_NAME_LENGTH + " characters)");
        }
    }

    private void validateDescription(String description) {
        List<String> errors = new ArrayList<>();
        validateDescription(description, errors);
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    private void validateDescription(String description, List<String> errors) {
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            errors.add("Workflow description too long (max " + MAX_DESCRIPTION_LENGTH + " characters)");
        }
    }

    private void validateUrl(String url) {
        List<String> errors = new ArrayList<>();
        validateUrl(url, errors);
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    private void validateUrl(String url, List<String> errors) {
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            errors.add("Invalid URL format: " + url);
        }
    }

    private void validateActions(int actionCount) {
        List<String> errors = new ArrayList<>();
        validateActions(actionCount, errors);
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    private void validateActions(int actionCount, List<String> errors) {
        if (actionCount > MAX_ACTIONS_PER_WORKFLOW) {
            errors.add("Too many actions (max " + MAX_ACTIONS_PER_WORKFLOW + ")");
        }
    }
}
