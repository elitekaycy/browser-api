package com.browserapi.recorder.controller;

import com.browserapi.action.model.Action;
import com.browserapi.recorder.model.RecorderSession;
import com.browserapi.recorder.service.EventCaptureService;
import com.browserapi.recorder.service.FrameStreamingService;
import com.browserapi.recorder.service.RecorderSessionManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for browser action recorder UI and REST API.
 * <p>
 * Provides:
 * - Thymeleaf page serving for recorder UI
 * - REST API for session management and recording control
 */
@Controller
@RequestMapping("/recorder")
@Tag(name = "Recorder", description = "Browser action recorder with real-time streaming")
public class RecorderController {

    private static final Logger log = LoggerFactory.getLogger(RecorderController.class);

    private final RecorderSessionManager sessionManager;
    private final FrameStreamingService frameStreamingService;
    private final EventCaptureService eventCaptureService;

    public RecorderController(
            RecorderSessionManager sessionManager,
            FrameStreamingService frameStreamingService,
            EventCaptureService eventCaptureService
    ) {
        this.sessionManager = sessionManager;
        this.frameStreamingService = frameStreamingService;
        this.eventCaptureService = eventCaptureService;
    }

    /**
     * Serve the recorder HTML page.
     *
     * @param model Spring MVC model for passing data to view
     * @return Thymeleaf template name
     */
    @GetMapping
    public String recorderPage(Model model) {
        model.addAttribute("appName", "Browser Action Recorder");
        model.addAttribute("version", "1.0.0");
        model.addAttribute("apiBaseUrl", "/api/v1");
        return "recorder";
    }

    // REST API Endpoints

    /**
     * Create a new recorder session.
     * Initializes a browser session and loads the specified URL.
     */
    @PostMapping("/api/v1/recorder/sessions")
    @ResponseBody
    @Operation(
            summary = "Create recorder session",
            description = "Creates a new recorder session with a browser page loaded at the specified URL."
    )
    public ResponseEntity<?> createSession(@RequestBody CreateSessionRequest request) {
        log.info("Create recorder session: url={}", request.url());

        try {
            RecorderSession session = sessionManager.createSession(request.url());

            // Set custom frame rate if provided
            if (request.frameRate() != null && request.frameRate() > 0 && request.frameRate() <= 30) {
                session.setFrameRate(request.frameRate());
            }

            CreateSessionResponse response = new CreateSessionResponse(
                    session.getSessionId().toString(),
                    session.getBrowserSessionId().toString(),
                    session.getUrl(),
                    session.getFrameRate()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Failed to create recorder session", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to create session",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Start recording and frame streaming for a session.
     */
    @PostMapping("/api/v1/recorder/sessions/{sessionId}/start")
    @ResponseBody
    @Operation(
            summary = "Start recording",
            description = "Starts recording browser interactions and streaming frames via WebSocket."
    )
    public ResponseEntity<?> startRecording(@PathVariable String sessionId) {
        log.info("Start recording: sessionId={}", sessionId);

        try {
            UUID uuid = UUID.fromString(sessionId);

            RecorderSession session = sessionManager.getSession(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

            // Start recording
            sessionManager.startRecording(uuid);

            // Enable event capture
            eventCaptureService.enableCapture(uuid, session.getBrowserSessionId());

            // Start frame streaming
            frameStreamingService.startStreaming(session);

            StartRecordingResponse response = new StartRecordingResponse(
                    "recording",
                    "/topic/recorder/" + sessionId + "/frames",
                    "/topic/recorder/" + sessionId + "/actions"
            );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Session not found",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to start recording", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to start recording",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Stop recording for a session.
     */
    @PostMapping("/api/v1/recorder/sessions/{sessionId}/stop")
    @ResponseBody
    @Operation(
            summary = "Stop recording",
            description = "Stops recording browser interactions and frame streaming."
    )
    public ResponseEntity<?> stopRecording(@PathVariable String sessionId) {
        log.info("Stop recording: sessionId={}", sessionId);

        try {
            UUID uuid = UUID.fromString(sessionId);

            RecorderSession session = sessionManager.getSession(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

            // Stop recording
            sessionManager.stopRecording(uuid);

            // Disable event capture
            eventCaptureService.disableCapture(uuid);

            // Stop frame streaming
            frameStreamingService.stopStreaming(uuid);

            StopRecordingResponse response = new StopRecordingResponse(
                    "stopped",
                    session.getActionCount()
            );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Session not found",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to stop recording", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to stop recording",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get recorded actions for a session.
     */
    @GetMapping("/api/v1/recorder/sessions/{sessionId}/actions")
    @ResponseBody
    @Operation(
            summary = "Get recorded actions",
            description = "Retrieves the list of actions captured during recording."
    )
    public ResponseEntity<?> getActions(@PathVariable String sessionId) {
        try {
            UUID uuid = UUID.fromString(sessionId);

            RecorderSession session = sessionManager.getSession(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

            GetActionsResponse response = new GetActionsResponse(
                    session.getRecordedActions(),
                    session.getActionCount()
            );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Session not found",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Close a recorder session.
     */
    @DeleteMapping("/api/v1/recorder/sessions/{sessionId}")
    @ResponseBody
    @Operation(
            summary = "Close recorder session",
            description = "Closes the recorder session and releases all resources."
    )
    public ResponseEntity<?> closeSession(@PathVariable String sessionId) {
        log.info("Close recorder session: sessionId={}", sessionId);

        try {
            UUID uuid = UUID.fromString(sessionId);

            // Stop streaming if active
            frameStreamingService.stopStreaming(uuid);

            // Disable event capture if active
            eventCaptureService.disableCapture(uuid);

            // Close session
            sessionManager.closeSession(uuid);

            return ResponseEntity.ok(Map.of(
                    "message", "Session closed successfully"
            ));

        } catch (Exception e) {
            log.error("Failed to close session", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to close session",
                    "message", e.getMessage()
            ));
        }
    }

    // Request/Response Models

    public record CreateSessionRequest(String url, Integer frameRate) {}

    public record CreateSessionResponse(
            String sessionId,
            String browserSessionId,
            String url,
            int frameRate
    ) {}

    public record StartRecordingResponse(
            String status,
            String framesUrl,
            String actionsUrl
    ) {}

    public record StopRecordingResponse(String status, int actionCount) {}

    public record GetActionsResponse(List<Action> actions, int count) {}
}
