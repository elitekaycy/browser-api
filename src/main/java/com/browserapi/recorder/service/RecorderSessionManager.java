package com.browserapi.recorder.service;

import com.browserapi.browser.BrowserManager;
import com.browserapi.browser.PageSession;
import com.browserapi.recorder.model.RecorderSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages recorder sessions and their lifecycle.
 * <p>
 * Responsibilities:
 * - Create and track recorder sessions
 * - Link recorder sessions to browser sessions
 * - Auto-cleanup idle sessions
 * - Provide session lookup and state management
 */
@Service
public class RecorderSessionManager {

    private static final Logger log = LoggerFactory.getLogger(RecorderSessionManager.class);

    /**
     * Default idle timeout: 30 minutes.
     * Sessions idle longer than this are automatically closed.
     */
    private static final long DEFAULT_IDLE_TIMEOUT_MS = 30 * 60 * 1000;

    /**
     * Active recorder sessions indexed by session ID.
     */
    private final Map<UUID, RecorderSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * Browser manager for creating and managing Playwright browser sessions.
     */
    private final BrowserManager browserManager;

    public RecorderSessionManager(BrowserManager browserManager) {
        this.browserManager = browserManager;
    }

    /**
     * Create a new recorder session with a browser session.
     *
     * @param url target URL to load and record
     * @return created recorder session
     */
    public RecorderSession createSession(String url) {
        log.info("Creating recorder session for URL: {}", url);

        // Create browser session via BrowserManager
        PageSession browserSession = browserManager.createSession(url);

        // Create recorder session and link to browser session
        RecorderSession recorderSession = new RecorderSession(url);
        recorderSession.setBrowserSessionId(browserSession.sessionId());

        // Track session
        activeSessions.put(recorderSession.getSessionId(), recorderSession);

        log.info("Recorder session created: sessionId={}, browserSessionId={}",
                recorderSession.getSessionId(), browserSession.sessionId());

        return recorderSession;
    }

    /**
     * Get recorder session by ID.
     *
     * @param sessionId session identifier
     * @return optional containing session if found
     */
    public Optional<RecorderSession> getSession(UUID sessionId) {
        RecorderSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.touch(); // Update last accessed timestamp
        }
        return Optional.ofNullable(session);
    }

    /**
     * Get browser session associated with a recorder session.
     *
     * @param recorderSessionId recorder session ID
     * @return optional containing browser PageSession if found
     */
    public Optional<PageSession> getBrowserSession(UUID recorderSessionId) {
        return getSession(recorderSessionId)
                .map(RecorderSession::getBrowserSessionId)
                .flatMap(browserSessionId -> {
                    // This would require adding a getSession method to BrowserManager
                    // For now, we'll need to store the PageSession reference in RecorderSession
                    // TODO: Update implementation once BrowserManager supports session lookup
                    log.warn("Browser session lookup not yet implemented");
                    return Optional.empty();
                });
    }

    /**
     * Start recording for a session.
     *
     * @param sessionId recorder session ID
     * @return true if started successfully
     */
    public boolean startRecording(UUID sessionId) {
        return getSession(sessionId)
                .map(session -> {
                    session.startRecording();
                    log.info("Recording started for session: {}", sessionId);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Stop recording for a session.
     *
     * @param sessionId recorder session ID
     * @return true if stopped successfully
     */
    public boolean stopRecording(UUID sessionId) {
        return getSession(sessionId)
                .map(session -> {
                    session.stopRecording();
                    log.info("Recording stopped for session: {}", sessionId);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Perform a mouse click at the specified coordinates in the browser.
     *
     * @param sessionId recorder session ID
     * @param x X coordinate
     * @param y Y coordinate
     */
    public void performClick(UUID sessionId, double x, double y) {
        RecorderSession recorderSession = getSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Recorder session not found: " + sessionId));

        UUID browserSessionId = recorderSession.getBrowserSessionId();
        if (browserSessionId == null) {
            throw new IllegalStateException("No browser session associated with recorder session: " + sessionId);
        }

        PageSession pageSession = browserManager.getSession(browserSessionId)
                .orElseThrow(() -> new IllegalStateException("Browser session not found: " + browserSessionId));

        log.debug("Performing click at ({}, {}) for session: {}", x, y, sessionId);
        pageSession.page().mouse().click(x, y);
        log.debug("Click performed successfully");
    }

    /**
     * Close a recorder session and its associated browser session.
     *
     * @param sessionId recorder session ID
     */
    public void closeSession(UUID sessionId) {
        RecorderSession session = activeSessions.remove(sessionId);

        if (session != null) {
            log.info("Closing recorder session: {}", sessionId);

            // Stop recording if active
            if (session.isRecording()) {
                session.stopRecording();
            }

            // Close associated browser session
            if (session.getBrowserSessionId() != null) {
                try {
                    browserManager.closeSession(session.getBrowserSessionId());
                    log.info("Closed browser session: {}", session.getBrowserSessionId());
                } catch (Exception e) {
                    log.error("Failed to close browser session: {}", session.getBrowserSessionId(), e);
                }
            }

            log.info("Recorder session closed: {}", sessionId);
        } else {
            log.warn("Attempted to close non-existent session: {}", sessionId);
        }
    }

    /**
     * Get count of active recorder sessions.
     *
     * @return number of active sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Scheduled task to cleanup idle sessions.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 5 * 60 * 1000) // Every 5 minutes
    public void cleanupIdleSessions() {
        log.debug("Running idle session cleanup");

        activeSessions.values().stream()
                .filter(session -> session.isIdleFor(DEFAULT_IDLE_TIMEOUT_MS))
                .map(RecorderSession::getSessionId)
                .forEach(sessionId -> {
                    log.info("Closing idle recorder session: {}", sessionId);
                    closeSession(sessionId);
                });
    }

    /**
     * Get all active sessions (for monitoring/debugging).
     *
     * @return map of session ID to RecorderSession
     */
    public Map<UUID, RecorderSession> getActiveSessions() {
        return Map.copyOf(activeSessions);
    }
}
