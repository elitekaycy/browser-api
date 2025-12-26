package com.browserapi.action.controller;

import com.browserapi.action.model.Action;
import com.browserapi.action.model.ActionResult;
import com.browserapi.action.model.ActionType;
import com.browserapi.action.service.ActionExecutor;
import com.browserapi.browser.BrowserManager;
import com.browserapi.browser.PageSession;
import com.browserapi.extraction.ExtractionType;
import com.browserapi.extraction.dto.ExtractionRequest;
import com.browserapi.extraction.dto.ExtractionResponse;
import com.browserapi.extraction.strategy.ExtractionStrategy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for executing browser actions.
 * Provides endpoints for action execution with optional data extraction.
 */
@RestController
@RequestMapping("/api/v1/actions")
@Tag(name = "Browser Actions", description = "Execute browser actions and workflows")
public class ActionController {

    private static final Logger log = LoggerFactory.getLogger(ActionController.class);

    private final BrowserManager browserManager;
    private final ActionExecutor actionExecutor;
    private final Map<ExtractionType, ExtractionStrategy> extractionStrategies;

    public ActionController(BrowserManager browserManager,
                           ActionExecutor actionExecutor,
                           List<ExtractionStrategy> strategyList) {
        this.browserManager = browserManager;
        this.actionExecutor = actionExecutor;
        this.extractionStrategies = strategyList.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ExtractionStrategy::getType,
                        java.util.function.Function.identity()
                ));
        log.info("ActionController initialized with {} extraction strategies", extractionStrategies.size());
    }

    @PostMapping("/execute")
    @Operation(
            summary = "Execute a sequence of browser actions",
            description = """
                    Execute browser actions on a page and optionally extract data or take screenshots.

                    Supports:
                    - Action chains (login, navigate, click, fill, etc.)
                    - Optional data extraction after actions
                    - Screenshot capture
                    - Continue-on-error mode

                    Example: Login flow with extraction
                    ```json
                    {
                      "url": "https://example.com/login",
                      "actions": [
                        {"type": "FILL", "selector": "#username", "value": "admin"},
                        {"type": "FILL", "selector": "#password", "value": "secret"},
                        {"type": "CLICK", "selector": "#login-btn"},
                        {"type": "WAIT_NAVIGATION"}
                      ],
                      "extract": {
                        "selector": ".dashboard",
                        "format": "HTML"
                      }
                    }
                    ```

                    Example: Screenshot after actions
                    ```json
                    {
                      "url": "https://example.com/form",
                      "actions": [
                        {"type": "FILL", "selector": "#email", "value": "test@example.com"},
                        {"type": "CLICK", "selector": "#submit"}
                      ],
                      "captureScreenshot": true
                    }
                    ```
                    """
    )
    public ResponseEntity<?> executeActions(@RequestBody ActionRequest request) {
        log.info("Action execution request: url={}, actions={}", request.url(), request.actions().size());

        PageSession session = null;
        long startTime = System.currentTimeMillis();

        try {
            // 1. Create browser session
            session = browserManager.createSession(request.url());
            log.info("Browser session created for action execution: sessionId={}", session.sessionId());

            // 2. Convert ActionSpecs to Actions
            List<Action> actions = request.actions().stream()
                    .map(ActionSpec::toAction)
                    .toList();

            // 3. Execute actions
            log.info("Executing {} actions (continueOnError={})", actions.size(), request.continueOnError());
            List<ActionResult> results = actionExecutor.executeSequence(
                    session.page(),
                    actions,
                    request.continueOnError()
            );

            // 4. Optional: Extract data after actions
            String extractedData = null;
            if (request.extract() != null) {
                log.info("Extracting data: selector={}, format={}",
                        request.extract().selector(), request.extract().format());
                extractedData = performExtraction(session, request.extract());
            }

            // 5. Optional: Capture screenshot
            String screenshot = null;
            if (request.captureScreenshot()) {
                log.info("Capturing screenshot after actions");
                screenshot = captureScreenshot(session);
            }

            // 6. Build response
            long totalExecutionMs = System.currentTimeMillis() - startTime;
            ActionResponse response = ActionResponse.from(
                    results,
                    extractedData,
                    screenshot,
                    session.page().url(),
                    totalExecutionMs
            );

            log.info("Action execution completed: success={}/{}, totalTime={}ms",
                    results.stream().filter(ActionResult::success).count(),
                    results.size(),
                    totalExecutionMs);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Action execution failed: url={}", request.url(), e);
            return ResponseEntity
                    .internalServerError()
                    .body(Map.of(
                            "error", "Action execution failed",
                            "message", e.getMessage(),
                            "url", request.url()
                    ));
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    @GetMapping("/types")
    @Operation(
            summary = "Get all supported action types",
            description = """
                    Returns a list of all action types that can be executed.

                    Supported actions:
                    - CLICK: Click an element
                    - FILL: Fill an input field
                    - SELECT: Select dropdown option
                    - SUBMIT: Submit a form
                    - WAIT: Wait for element or duration
                    - WAIT_NAVIGATION: Wait for page navigation
                    - SCROLL: Scroll to element
                    - HOVER: Mouse hover
                    - PRESS_KEY: Keyboard input
                    - SCREENSHOT: Capture screenshot
                    - NAVIGATE: Navigate to URL
                    - CHECK: Check/uncheck checkbox
                    - CLEAR: Clear input field
                    """
    )
    public ResponseEntity<List<ActionTypeInfo>> getActionTypes() {
        List<ActionTypeInfo> types = Arrays.stream(ActionType.values())
                .map(type -> new ActionTypeInfo(
                        type.name(),
                        getActionDescription(type),
                        getActionRequirements(type)
                ))
                .toList();

        return ResponseEntity.ok(types);
    }

    // Helper methods

    private String performExtraction(PageSession session, ExtractionSpec spec) {
        try {
            ExtractionType type = ExtractionType.valueOf(spec.format().toUpperCase());
            ExtractionStrategy strategy = extractionStrategies.get(type);

            if (strategy == null) {
                throw new IllegalArgumentException("Unsupported extraction format: " + spec.format());
            }

            // Build extraction request (url not needed since we already have the session)
            ExtractionRequest request = new ExtractionRequest(
                    session.url(),
                    type,
                    spec.selector()
            );

            ExtractionResponse response = strategy.extract(request, session);
            return response.data();
        } catch (Exception e) {
            log.error("Extraction failed: selector={}, format={}", spec.selector(), spec.format(), e);
            throw new RuntimeException("Extraction failed: " + e.getMessage(), e);
        }
    }

    private String captureScreenshot(PageSession session) {
        try {
            byte[] screenshotBytes = session.page().screenshot();
            return Base64.getEncoder().encodeToString(screenshotBytes);
        } catch (Exception e) {
            log.error("Screenshot capture failed", e);
            throw new RuntimeException("Screenshot capture failed: " + e.getMessage(), e);
        }
    }

    private String getActionDescription(ActionType type) {
        return switch (type) {
            case CLICK -> "Click an element on the page";
            case FILL -> "Fill an input field with text";
            case SELECT -> "Select an option from a dropdown";
            case SUBMIT -> "Submit a form";
            case WAIT -> "Wait for an element to appear or sleep for duration";
            case WAIT_NAVIGATION -> "Wait for page navigation to complete";
            case SCROLL -> "Scroll to an element";
            case HOVER -> "Hover over an element";
            case PRESS_KEY -> "Press a keyboard key";
            case PRESS_ENTER -> "Fill an input field and press Enter (for search boxes)";
            case SCREENSHOT -> "Take a screenshot of the page";
            case NAVIGATE -> "Navigate to a URL";
            case CHECK -> "Check or uncheck a checkbox";
            case CLEAR -> "Clear an input field";
            case EXTRACT -> "Extract data from an element (text, HTML, attribute, or JSON)";
        };
    }

    private ActionRequirements getActionRequirements(ActionType type) {
        return switch (type) {
            case CLICK, SCROLL, HOVER, SUBMIT, CLEAR ->
                new ActionRequirements(true, false, false);
            case FILL, SELECT, PRESS_KEY, PRESS_ENTER ->
                new ActionRequirements(true, true, false);
            case NAVIGATE ->
                new ActionRequirements(false, true, false);
            case WAIT ->
                new ActionRequirements(false, false, true);
            case WAIT_NAVIGATION, SCREENSHOT ->
                new ActionRequirements(false, false, false);
            case CHECK ->
                new ActionRequirements(true, false, false);
            case EXTRACT ->
                new ActionRequirements(true, false, false);
        };
    }

    // Request/Response models

    public record ActionRequest(
            @Parameter(description = "URL to navigate to", required = true)
            String url,

            @Parameter(description = "List of actions to execute", required = true)
            List<ActionSpec> actions,

            @Parameter(description = "Optional extraction after actions")
            ExtractionSpec extract,

            @Parameter(description = "Capture screenshot after actions")
            boolean captureScreenshot,

            @Parameter(description = "Continue executing actions if one fails")
            boolean continueOnError
    ) {}

    public record ActionSpec(
            @Parameter(description = "Type of action", required = true)
            ActionType type,

            @Parameter(description = "CSS selector for element")
            String selector,

            @Parameter(description = "Value to fill/select/navigate")
            String value,

            @Parameter(description = "Wait timeout in milliseconds")
            Integer waitMs,

            @Parameter(description = "Human-readable description")
            String description,

            @Parameter(description = "Extract type (TEXT, HTML, ATTRIBUTE, JSON) - for EXTRACT action")
            String extractType,

            @Parameter(description = "Attribute name - for EXTRACT action with ATTRIBUTE type")
            String attributeName,

            @Parameter(description = "JSONPath expression - for EXTRACT action with JSON type")
            String jsonPath
    ) {
        public Action toAction() {
            String desc = description != null ? description :
                    type + " " + (selector != null ? selector : value != null ? value : "");
            return new Action(type, selector, value, waitMs, desc, extractType, attributeName, jsonPath);
        }
    }

    public record ExtractionSpec(
            @Parameter(description = "CSS selector for extraction", required = true)
            String selector,

            @Parameter(description = "Extraction format (HTML, CSS, JSON)", required = true)
            String format
    ) {}

    public record ActionResponse(
            List<ActionResult> results,
            String extractedData,
            String screenshotBase64,
            String finalUrl,
            long totalExecutionMs,
            LocalDateTime executedAt,
            boolean allSuccessful,
            int successCount,
            int failureCount
    ) {
        public static ActionResponse from(
                List<ActionResult> results,
                String extractedData,
                String screenshot,
                String finalUrl,
                long totalExecutionMs
        ) {
            long successCount = results.stream().filter(ActionResult::success).count();
            int failureCount = results.size() - (int) successCount;
            boolean allSuccessful = failureCount == 0;

            return new ActionResponse(
                    results,
                    extractedData,
                    screenshot,
                    finalUrl,
                    totalExecutionMs,
                    LocalDateTime.now(),
                    allSuccessful,
                    (int) successCount,
                    failureCount
            );
        }
    }

    public record ActionTypeInfo(
            String name,
            String description,
            ActionRequirements requirements
    ) {}

    public record ActionRequirements(
            boolean requiresSelector,
            boolean requiresValue,
            boolean optionalWaitMs
    ) {}
}
