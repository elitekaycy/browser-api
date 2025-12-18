package com.browserapi.recorder.model;

import com.browserapi.action.model.Action;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents an active browser recording session.
 * <p>
 * Manages the state of a recording session including:
 * - Browser session link (via BrowserManager)
 * - Recording state and captured actions
 * - Frame streaming configuration
 * - Session lifecycle timestamps
 */
@Getter
@Setter
public class RecorderSession {

    /**
     * Unique identifier for this recorder session.
     */
    private final UUID sessionId;

    /**
     * Browser session ID from BrowserManager.
     * Links this recorder session to an active Playwright page.
     */
    private UUID browserSessionId;

    /**
     * URL being recorded.
     */
    private String url;

    /**
     * Whether recording is currently active.
     * When true, events are captured and frames are streamed.
     */
    private boolean recording;

    /**
     * Actions captured during this recording session.
     * List is populated in real-time as user interacts with the page.
     */
    private final List<Action> recordedActions;

    /**
     * Frame rate for screenshot streaming (frames per second).
     * Default: 5fps (good balance between smoothness and bandwidth)
     */
    private int frameRate;

    /**
     * When this recording session was created.
     */
    private final LocalDateTime startedAt;

    /**
     * Last time this session was accessed (updated on activity).
     */
    private LocalDateTime lastAccessedAt;

    /**
     * Create a new recorder session.
     *
     * @param url target URL to record
     */
    public RecorderSession(String url) {
        this.sessionId = UUID.randomUUID();
        this.url = url;
        this.recording = false;
        this.recordedActions = new ArrayList<>();
        this.frameRate = 5; // Default 5fps
        this.startedAt = LocalDateTime.now();
        this.lastAccessedAt = LocalDateTime.now();
    }

    /**
     * Add a captured action to this session.
     *
     * @param action the action to record
     */
    public void addAction(Action action) {
        this.recordedActions.add(action);
        touch();
    }

    /**
     * Start recording.
     * Enables event capture and frame streaming.
     */
    public void startRecording() {
        this.recording = true;
        touch();
    }

    /**
     * Stop recording.
     * Disables event capture and frame streaming.
     */
    public void stopRecording() {
        this.recording = false;
        touch();
    }

    /**
     * Update last accessed timestamp.
     * Called on any session activity to prevent idle cleanup.
     */
    public void touch() {
        this.lastAccessedAt = LocalDateTime.now();
    }

    /**
     * Check if session has been idle for specified duration.
     *
     * @param idleTimeoutMs idle timeout in milliseconds
     * @return true if session is idle
     */
    public boolean isIdleFor(long idleTimeoutMs) {
        LocalDateTime idleThreshold = LocalDateTime.now().minusNanos(idleTimeoutMs * 1_000_000);
        return lastAccessedAt.isBefore(idleThreshold);
    }

    /**
     * Get number of actions recorded.
     *
     * @return action count
     */
    public int getActionCount() {
        return recordedActions.size();
    }
}
