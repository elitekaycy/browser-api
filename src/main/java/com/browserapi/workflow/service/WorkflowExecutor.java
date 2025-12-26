package com.browserapi.workflow.service;

import com.browserapi.action.model.Action;
import com.browserapi.action.model.ActionResult;
import com.browserapi.action.service.ActionExecutor;
import com.browserapi.browser.BrowserManager;
import com.browserapi.browser.PageSession;
import com.browserapi.workflow.entity.Workflow;
import com.browserapi.workflow.exception.WorkflowExecutionException;
import com.browserapi.workflow.model.WorkflowExecutionResult;
import com.browserapi.workflow.repository.WorkflowRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for executing workflows with parameter substitution.
 * Handles browser session management, action execution, and statistics tracking.
 */
@Service
public class WorkflowExecutor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutor.class);

    private final BrowserManager browserManager;
    private final ActionExecutor actionExecutor;
    private final WorkflowRepository repository;

    public WorkflowExecutor(BrowserManager browserManager,
                           ActionExecutor actionExecutor,
                           WorkflowRepository repository) {
        this.browserManager = browserManager;
        this.actionExecutor = actionExecutor;
        this.repository = repository;
    }

    /**
     * Execute a workflow without parameters.
     *
     * @param workflow the workflow to execute
     * @return execution result
     */
    public WorkflowExecutionResult execute(Workflow workflow) {
        return execute(workflow, Map.of());
    }

    /**
     * Execute a workflow with parameter substitution.
     *
     * @param workflow the workflow to execute
     * @param parameters parameters to substitute in actions (e.g., "${username}" -> "admin")
     * @return execution result
     */
    public WorkflowExecutionResult execute(Workflow workflow, Map<String, String> parameters) {
        log.info("Executing workflow: id={}, name={}, parameters={}",
                workflow.getId(), workflow.getName(), parameters.keySet());

        long startTime = System.currentTimeMillis();
        PageSession session = null;

        try {
            // 1. Get actions and substitute parameters
            List<Action> actions = workflow.getActions();
            log.debug("Workflow has {} actions", actions.size());

            List<Action> substitutedActions = substituteParameters(actions, parameters);

            // 2. Create browser session
            log.debug("Creating browser session for URL: {}", workflow.getUrl());
            session = browserManager.createSession(workflow.getUrl());

            // 3. Execute actions
            log.info("Executing {} actions for workflow: {}", substitutedActions.size(), workflow.getName());
            List<ActionResult> results = actionExecutor.executeSequence(
                    session.page(),
                    substitutedActions
            );

            // 4. Determine overall success
            boolean success = results.stream().allMatch(ActionResult::success);
            long executionTime = System.currentTimeMillis() - startTime;

            log.info("Workflow execution completed: id={}, success={}, time={}ms",
                    workflow.getId(), success, executionTime);

            // 5. Update workflow statistics
            workflow.recordExecution(success, executionTime);
            repository.save(workflow);
            log.debug("Updated workflow statistics: totalExecutions={}, successRate={}%",
                    workflow.getTotalExecutions(), workflow.getSuccessRate());

            // 6. Build and return result
            return new WorkflowExecutionResult(
                    workflow.getId(),
                    workflow.getName(),
                    success,
                    results,
                    executionTime,
                    session.page().url(),
                    LocalDateTime.now()
            );

        } catch (JsonProcessingException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Failed to deserialize workflow actions: id={}", workflow.getId(), e);

            // Record failed execution
            workflow.recordExecution(false, executionTime);
            repository.save(workflow);

            throw new WorkflowExecutionException(
                    workflow.getId(),
                    "Failed to deserialize workflow actions: " + e.getMessage(),
                    e
            );

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Workflow execution failed: id={}, name={}",
                    workflow.getId(), workflow.getName(), e);

            // Record failed execution
            workflow.recordExecution(false, executionTime);
            repository.save(workflow);

            throw new WorkflowExecutionException(
                    workflow.getId(),
                    "Workflow execution failed: " + e.getMessage(),
                    e
            );

        } finally {
            // Always close browser session
            if (session != null) {
                try {
                    session.close();
                    log.debug("Closed browser session for workflow: {}", workflow.getId());
                } catch (Exception e) {
                    log.warn("Failed to close browser session: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Substitute parameters in all actions.
     *
     * @param actions original actions
     * @param parameters parameter map
     * @return actions with substituted values
     */
    private List<Action> substituteParameters(List<Action> actions, Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            log.debug("No parameters to substitute");
            return actions;
        }

        log.debug("Substituting {} parameters in {} actions", parameters.size(), actions.size());

        return actions.stream()
                .map(action -> substituteAction(action, parameters))
                .toList();
    }

    /**
     * Substitute parameters in a single action.
     *
     * @param action original action
     * @param parameters parameter map
     * @return action with substituted values
     */
    private Action substituteAction(Action action, Map<String, String> parameters) {
        String selector = substituteString(action.selector(), parameters);
        String value = substituteString(action.value(), parameters);
        String description = substituteString(action.description(), parameters);

        // Only create new action if something changed
        if (!selector.equals(action.selector()) ||
            !value.equals(action.value()) ||
            !description.equals(action.description())) {

            log.trace("Substituted action: {} -> {}", action.description(), description);

            return new Action(
                    action.type(),
                    selector,
                    value,
                    action.waitMs(),
                    description,
                    action.extractType(),
                    action.attributeName(),
                    action.jsonPath()
            );
        }

        return action;
    }

    /**
     * Substitute parameters in a string using ${paramName} placeholders.
     *
     * @param input input string
     * @param parameters parameter map
     * @return string with substituted values
     */
    private String substituteString(String input, Map<String, String> parameters) {
        if (input == null) {
            return null;
        }

        String result = input;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            result = result.replace(placeholder, entry.getValue());
        }

        return result;
    }
}
