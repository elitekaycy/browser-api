package com.browserapi.workflow.controller;

import com.browserapi.workflow.entity.Workflow;
import com.browserapi.workflow.exception.WorkflowNotFoundException;
import com.browserapi.workflow.exception.WorkflowValidationException;
import com.browserapi.workflow.model.*;
import com.browserapi.workflow.service.WorkflowExecutor;
import com.browserapi.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API controller for workflow management.
 * Provides endpoints for CRUD operations, execution, search, and analytics.
 */
@RestController
@RequestMapping("/api/v1/workflows")
@Tag(name = "Workflow Management", description = "Manage and execute reusable browser workflows")
public class WorkflowController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowController.class);

    private final WorkflowService workflowService;
    private final WorkflowExecutor workflowExecutor;

    public WorkflowController(WorkflowService workflowService, WorkflowExecutor workflowExecutor) {
        this.workflowService = workflowService;
        this.workflowExecutor = workflowExecutor;
    }

    // CRUD Endpoints

    @PostMapping
    @Operation(
            summary = "Create a new workflow",
            description = """
                    Create a reusable workflow with a sequence of browser actions.

                    Example request:
                    ```json
                    {
                      "name": "Login to Example.com",
                      "description": "Automated login workflow",
                      "url": "https://example.com/login",
                      "actions": [
                        {"type": "FILL", "selector": "#username", "value": "${username}"},
                        {"type": "FILL", "selector": "#password", "value": "${password}"},
                        {"type": "CLICK", "selector": "#login-btn"},
                        {"type": "WAIT_NAVIGATION"}
                      ],
                      "tags": ["login", "authentication"],
                      "createdBy": "admin"
                    }
                    ```

                    Use ${paramName} placeholders for parameterized workflows.
                    """
    )
    public ResponseEntity<?> createWorkflow(@RequestBody WorkflowCreateRequest request) {
        log.info("Create workflow request: name={}", request.name());

        try {
            Workflow created = workflowService.create(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);

        } catch (WorkflowValidationException e) {
            log.warn("Workflow validation failed: {}", e.getValidationErrors());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Validation failed",
                    "validationErrors", e.getValidationErrors()
            ));

        } catch (Exception e) {
            log.error("Failed to create workflow", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to create workflow",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping
    @Operation(
            summary = "List all workflows",
            description = "Get all workflows with optional pagination."
    )
    public ResponseEntity<Page<Workflow>> getAllWorkflows(
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Workflow> workflows = workflowService.getAll(pageable);
        return ResponseEntity.ok(workflows);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get workflow by ID",
            description = "Retrieve a specific workflow by its unique identifier."
    )
    public ResponseEntity<?> getWorkflow(@PathVariable String id) {
        try {
            Workflow workflow = workflowService.getById(id);
            return ResponseEntity.ok(workflow);

        } catch (WorkflowNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update workflow",
            description = """
                    Update an existing workflow. All fields are optional - only provided fields will be updated.

                    Example request:
                    ```json
                    {
                      "name": "Updated Login Workflow",
                      "tags": ["login", "auth", "production"]
                    }
                    ```
                    """
    )
    public ResponseEntity<?> updateWorkflow(
            @PathVariable String id,
            @RequestBody WorkflowUpdateRequest request
    ) {
        log.info("Update workflow request: id={}", id);

        try {
            Workflow updated = workflowService.update(id, request);
            return ResponseEntity.ok(updated);

        } catch (WorkflowNotFoundException e) {
            return ResponseEntity.notFound().build();

        } catch (WorkflowValidationException e) {
            log.warn("Workflow validation failed: {}", e.getValidationErrors());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Validation failed",
                    "validationErrors", e.getValidationErrors()
            ));

        } catch (Exception e) {
            log.error("Failed to update workflow: id={}", id, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to update workflow",
                    "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete workflow",
            description = "Delete a workflow by its unique identifier."
    )
    public ResponseEntity<?> deleteWorkflow(@PathVariable String id) {
        log.info("Delete workflow request: id={}", id);

        try {
            workflowService.delete(id);
            return ResponseEntity.ok(Map.of("message", "Workflow deleted successfully"));

        } catch (WorkflowNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Execution Endpoint

    @PostMapping("/{id}/execute")
    @Operation(
            summary = "Execute a workflow",
            description = """
                    Execute a workflow with optional parameter substitution.

                    Example request (without parameters):
                    ```json
                    {}
                    ```

                    Example request (with parameters):
                    ```json
                    {
                      "username": "admin",
                      "password": "secret123",
                      "email": "admin@example.com"
                    }
                    ```

                    Parameters are substituted into actions containing ${paramName} placeholders.
                    Returns detailed execution results including action-level status.
                    """
    )
    public ResponseEntity<?> executeWorkflow(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> parameters
    ) {
        log.info("Execute workflow request: id={}, parameters={}",
                id, parameters != null ? parameters.keySet() : "none");

        try {
            Workflow workflow = workflowService.getById(id);

            Map<String, String> params = parameters != null ? parameters : Map.of();
            WorkflowExecutionResult result = workflowExecutor.execute(workflow, params);

            return ResponseEntity.ok(result);

        } catch (WorkflowNotFoundException e) {
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("Workflow execution failed: id={}", id, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Workflow execution failed",
                    "message", e.getMessage()
            ));
        }
    }

    // Search & Filter Endpoints

    @GetMapping("/search")
    @Operation(
            summary = "Search workflows by name",
            description = "Search for workflows by name (case-insensitive partial match)."
    )
    public ResponseEntity<List<Workflow>> searchWorkflows(
            @Parameter(description = "Name to search for", required = true)
            @RequestParam String name
    ) {
        List<Workflow> results = workflowService.searchByName(name);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/by-tag")
    @Operation(
            summary = "Find workflows by tag",
            description = "Filter workflows that contain a specific tag."
    )
    public ResponseEntity<List<Workflow>> findByTag(
            @Parameter(description = "Tag to filter by", required = true)
            @RequestParam String tag
    ) {
        List<Workflow> results = workflowService.findByTag(tag);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/by-creator")
    @Operation(
            summary = "Find workflows by creator",
            description = "Filter workflows created by a specific user."
    )
    public ResponseEntity<List<Workflow>> findByCreator(
            @Parameter(description = "Creator username", required = true)
            @RequestParam String creator
    ) {
        List<Workflow> results = workflowService.findByCreator(creator);
        return ResponseEntity.ok(results);
    }

    // Analytics Endpoints

    @GetMapping("/most-executed")
    @Operation(
            summary = "Get most executed workflows",
            description = "Returns the top 10 workflows with the highest execution count."
    )
    public ResponseEntity<List<Workflow>> getMostExecuted() {
        List<Workflow> results = workflowService.getMostExecuted();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/most-successful")
    @Operation(
            summary = "Get most successful workflows",
            description = "Returns workflows ordered by success rate (only workflows with at least one execution)."
    )
    public ResponseEntity<Page<Workflow>> getMostSuccessful(
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Workflow> results = workflowService.getMostSuccessful(pageable);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/recently-executed")
    @Operation(
            summary = "Get recently executed workflows",
            description = "Returns the top 10 most recently executed workflows."
    )
    public ResponseEntity<List<Workflow>> getRecentlyExecuted() {
        List<Workflow> results = workflowService.getRecentlyExecuted();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/recently-created")
    @Operation(
            summary = "Get recently created workflows",
            description = "Returns the top 10 most recently created workflows."
    )
    public ResponseEntity<List<Workflow>> getRecentlyCreated() {
        List<Workflow> results = workflowService.getRecentlyCreated();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/never-executed")
    @Operation(
            summary = "Get workflows that have never been executed",
            description = "Returns workflows with zero executions."
    )
    public ResponseEntity<List<Workflow>> getNeverExecuted() {
        List<Workflow> results = workflowService.getNeverExecuted();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/statistics")
    @Operation(
            summary = "Get workflow statistics",
            description = """
                    Get aggregated statistics across all workflows.

                    Returns:
                    - Total number of workflows
                    - Total executions across all workflows
                    - Successful executions
                    - Failed executions
                    - Average execution time
                    - Global success rate
                    """
    )
    public ResponseEntity<WorkflowStatistics> getStatistics() {
        WorkflowStatistics stats = workflowService.getStatistics();
        return ResponseEntity.ok(stats);
    }
}
