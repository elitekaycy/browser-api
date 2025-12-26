package com.browserapi.action.service;

import com.browserapi.action.model.Action;
import com.browserapi.action.model.ActionResult;
import com.browserapi.action.model.ActionType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Service for executing browser actions on Playwright pages.
 * Supports sequential execution of action chains with error handling.
 */
@Service
public class ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutor.class);

    private static final int DEFAULT_WAIT_TIMEOUT_MS = 30000;
    private static final int DEFAULT_NAVIGATION_TIMEOUT_MS = 30000;

    /**
     * Execute a single action on the page.
     *
     * @param page the Playwright page to execute on
     * @param action the action to execute
     * @return ActionResult containing execution status
     */
    public ActionResult execute(Page page, Action action) {
        log.info("Executing action: {}", action.description());
        long startTime = System.currentTimeMillis();

        try {
            switch (action.type()) {
                case CLICK -> {
                    executeClick(page, action);
                }
                case FILL -> {
                    executeFill(page, action);
                }
                case SELECT -> {
                    executeSelect(page, action);
                }
                case SUBMIT -> {
                    executeSubmit(page, action);
                }
                case WAIT -> {
                    executeWait(page, action);
                }
                case WAIT_NAVIGATION -> {
                    executeWaitNavigation(page, action);
                }
                case SCROLL -> {
                    executeScroll(page, action);
                }
                case HOVER -> {
                    executeHover(page, action);
                }
                case PRESS_KEY -> {
                    executePressKey(page, action);
                }
                case PRESS_ENTER -> {
                    executePressEnter(page, action);
                }
                case SCREENSHOT -> {
                    return executeScreenshot(page, action, startTime);
                }
                case NAVIGATE -> {
                    executeNavigate(page, action);
                }
                case CHECK -> {
                    executeCheck(page, action);
                }
                case CLEAR -> {
                    executeClear(page, action);
                }
                case EXTRACT -> {
                    return executeExtract(page, action, startTime);
                }
                default -> throw new IllegalArgumentException("Unsupported action type: " + action.type());
            }

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Action executed successfully: {} ({}ms)", action.description(), executionTime);
            return ActionResult.success(action, executionTime, page.url());

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Action failed: {} - {}", action.description(), e.getMessage(), e);
            return ActionResult.failure(action, executionTime, e.getMessage(), page.url());
        }
    }

    /**
     * Execute multiple actions sequentially on the page.
     * Stops execution on first failure unless continueOnError is true.
     *
     * @param page the Playwright page to execute on
     * @param actions list of actions to execute
     * @return list of ActionResults for each action
     */
    public List<ActionResult> executeSequence(Page page, List<Action> actions) {
        return executeSequence(page, actions, false);
    }

    /**
     * Execute multiple actions sequentially on the page.
     *
     * @param page the Playwright page to execute on
     * @param actions list of actions to execute
     * @param continueOnError whether to continue executing after a failure
     * @return list of ActionResults for each action
     */
    public List<ActionResult> executeSequence(Page page, List<Action> actions, boolean continueOnError) {
        log.info("Executing action sequence: {} actions, continueOnError={}", actions.size(), continueOnError);
        List<ActionResult> results = new ArrayList<>();

        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);
            log.info("Executing action {}/{}: {}", i + 1, actions.size(), action.description());

            ActionResult result = execute(page, action);
            results.add(result);

            if (!result.success() && !continueOnError) {
                log.warn("Action failed, stopping sequence at step {}/{}", i + 1, actions.size());
                break;
            }
        }

        long successCount = results.stream().filter(ActionResult::success).count();
        log.info("Action sequence completed: {}/{} successful", successCount, results.size());
        return results;
    }

    // Private execution methods for each action type

    private void executeClick(Page page, Action action) {
        validateSelector(action);
        page.click(action.selector());
    }

    private void executeFill(Page page, Action action) {
        validateSelector(action);
        validateValue(action);
        page.fill(action.selector(), action.value());
    }

    private void executeSelect(Page page, Action action) {
        validateSelector(action);
        validateValue(action);
        page.selectOption(action.selector(), new SelectOption().setValue(action.value()));
    }

    private void executeSubmit(Page page, Action action) {
        validateSelector(action);
        // Submit form by clicking submit button or pressing Enter on form
        page.dispatchEvent(action.selector(), "submit");
    }

    private void executeWait(Page page, Action action) {
        if (action.selector() != null) {
            // Wait for element
            int timeout = action.waitMs() != null ? action.waitMs() : DEFAULT_WAIT_TIMEOUT_MS;
            page.waitForSelector(action.selector(), new Page.WaitForSelectorOptions().setTimeout(timeout));
        } else if (action.waitMs() != null) {
            // Simple sleep
            try {
                Thread.sleep(action.waitMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Wait interrupted", e);
            }
        } else {
            throw new IllegalArgumentException("WAIT action requires either selector or waitMs");
        }
    }

    private void executeWaitNavigation(Page page, Action action) {
        int timeout = action.waitMs() != null ? action.waitMs() : DEFAULT_NAVIGATION_TIMEOUT_MS;
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD,
                new Page.WaitForLoadStateOptions().setTimeout(timeout));
    }

    private void executeScroll(Page page, Action action) {
        validateSelector(action);
        page.evaluate("element => element.scrollIntoView({behavior: 'smooth', block: 'center'})",
                page.querySelector(action.selector()));
    }

    private void executeHover(Page page, Action action) {
        validateSelector(action);
        page.hover(action.selector());
    }

    private void executePressKey(Page page, Action action) {
        validateValue(action);
        if (action.selector() != null) {
            page.press(action.selector(), action.value());
        } else {
            page.keyboard().press(action.value());
        }
    }

    private void executePressEnter(Page page, Action action) {
        validateSelector(action);
        // Fill the input field with the value
        if (action.value() != null && !action.value().isEmpty()) {
            page.fill(action.selector(), action.value());
        }
        // Press Enter key on the input field
        page.press(action.selector(), "Enter");
        log.info("Pressed Enter on input: {}", action.selector());
    }

    private ActionResult executeScreenshot(Page page, Action action, long startTime) {
        byte[] screenshotBytes = page.screenshot();
        String screenshotBase64 = Base64.getEncoder().encodeToString(screenshotBytes);
        long executionTime = System.currentTimeMillis() - startTime;
        log.info("Screenshot captured: {} bytes ({}ms)", screenshotBytes.length, executionTime);
        return ActionResult.success(action, executionTime, page.url(), screenshotBase64);
    }

    private void executeNavigate(Page page, Action action) {
        validateValue(action);
        page.navigate(action.value());
    }

    private void executeCheck(Page page, Action action) {
        validateSelector(action);
        boolean shouldCheck = action.value() == null || action.value().equalsIgnoreCase("true");
        page.setChecked(action.selector(), shouldCheck);
    }

    private void executeClear(Page page, Action action) {
        validateSelector(action);
        page.fill(action.selector(), "");
    }

    private ActionResult executeExtract(Page page, Action action, long startTime) {
        validateSelector(action);

        if (action.extractType() == null || action.extractType().isBlank()) {
            throw new IllegalArgumentException("EXTRACT action requires extractType (TEXT, HTML, ATTRIBUTE, or JSON)");
        }

        // Wait for element to be present
        page.waitForSelector(action.selector(), new Page.WaitForSelectorOptions().setTimeout(DEFAULT_WAIT_TIMEOUT_MS));

        String extractedData;
        switch (action.extractType().toUpperCase()) {
            case "TEXT" -> {
                extractedData = page.locator(action.selector()).first().innerText();
                log.info("Extracted text: {}", truncate(extractedData, 100));
            }
            case "HTML" -> {
                extractedData = page.locator(action.selector()).first().innerHTML();
                log.info("Extracted HTML: {} characters", extractedData.length());
            }
            case "ATTRIBUTE" -> {
                if (action.attributeName() == null || action.attributeName().isBlank()) {
                    throw new IllegalArgumentException("EXTRACT action with ATTRIBUTE type requires attributeName");
                }
                extractedData = page.locator(action.selector()).first().getAttribute(action.attributeName());
                if (extractedData == null) {
                    extractedData = "";
                    log.warn("Attribute '{}' not found on element", action.attributeName());
                }
                log.info("Extracted attribute '{}': {}", action.attributeName(), truncate(extractedData, 100));
            }
            case "JSON" -> {
                String text = page.locator(action.selector()).first().innerText();
                // For now, return the raw text. JSONPath processing can be added later if needed.
                extractedData = text;
                log.info("Extracted JSON text: {}", truncate(extractedData, 100));

                // If jsonPath is provided, log a warning that it's not yet implemented
                if (action.jsonPath() != null && !action.jsonPath().isBlank()) {
                    log.warn("JSONPath filtering not yet implemented, returning raw JSON text");
                }
            }
            default -> throw new IllegalArgumentException("Unknown extract type: " + action.extractType() +
                ". Must be TEXT, HTML, ATTRIBUTE, or JSON");
        }

        long executionTime = System.currentTimeMillis() - startTime;
        log.info("Data extraction completed: {} ({}ms)", action.description(), executionTime);
        return ActionResult.success(action, executionTime, page.url(), extractedData);
    }

    // Validation helpers

    private void validateSelector(Action action) {
        if (action.selector() == null || action.selector().isBlank()) {
            throw new IllegalArgumentException(
                    action.type() + " action requires a selector");
        }
    }

    private void validateValue(Action action) {
        if (action.value() == null || action.value().isBlank()) {
            throw new IllegalArgumentException(
                    action.type() + " action requires a value");
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
