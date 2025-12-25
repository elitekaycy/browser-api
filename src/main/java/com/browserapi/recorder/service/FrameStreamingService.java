package com.browserapi.recorder.service;

import com.browserapi.browser.BrowserManager;
import com.browserapi.browser.PageSession;
import com.browserapi.recorder.model.RecorderSession;
import com.browserapi.recorder.service.CDPDOMSnapshotService.DOMSnapshotData;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Service for streaming browser screenshots to frontend via WebSocket.
 * <p>
 * Responsibilities:
 * - Capture screenshots from Playwright pages at configured FPS
 * - Encode screenshots to Base64
 * - Broadcast frames via WebSocket to connected clients
 * - Handle backpressure (skip frames if capturing too fast)
 * - Manage streaming threads for each session
 */
@Service
public class FrameStreamingService {

    private static final Logger log = LoggerFactory.getLogger(FrameStreamingService.class);

    /**
     * WebSocket messaging template for broadcasting frames.
     */
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Browser manager for accessing page sessions.
     */
    private final BrowserManager browserManager;

    /**
     * CDP DOM snapshot service for capturing DOM instead of screenshots.
     */
    private final CDPDOMSnapshotService domSnapshotService;

    /**
     * Executor service for running streaming threads.
     */
    private final ExecutorService executorService;

    /**
     * Active streaming tasks indexed by session ID.
     */
    private final Map<UUID, Future<?>> activeStreams;

    /**
     * Frame sequence numbers for each session.
     */
    private final Map<UUID, Long> frameSequences;

    /**
     * Use DOM snapshots instead of screenshots (default: true).
     * Set to false to fall back to screenshot mode.
     */
    private final Map<UUID, Boolean> useDOMSnapshot;

    public FrameStreamingService(
            SimpMessagingTemplate messagingTemplate,
            BrowserManager browserManager,
            CDPDOMSnapshotService domSnapshotService
    ) {
        this.messagingTemplate = messagingTemplate;
        this.browserManager = browserManager;
        this.domSnapshotService = domSnapshotService;
        this.executorService = Executors.newCachedThreadPool();
        this.activeStreams = new ConcurrentHashMap<>();
        this.frameSequences = new ConcurrentHashMap<>();
        this.useDOMSnapshot = new ConcurrentHashMap<>();
    }

    /**
     * Start streaming frames for a recorder session.
     *
     * @param session recorder session to stream
     */
    public void startStreaming(RecorderSession session) {
        startStreaming(session, true); // Default to DOM snapshots
    }

    /**
     * Start streaming frames for a recorder session with specified mode.
     *
     * @param session recorder session to stream
     * @param useDOMSnapshotMode true to use DOM snapshots, false for screenshots
     */
    public void startStreaming(RecorderSession session, boolean useDOMSnapshotMode) {
        UUID sessionId = session.getSessionId();

        if (activeStreams.containsKey(sessionId)) {
            log.warn("Streaming already active for session: {}", sessionId);
            return;
        }

        log.info("Starting frame streaming for session: {} at {}fps (mode: {})",
                sessionId, session.getFrameRate(), useDOMSnapshotMode ? "DOM" : "Screenshot");

        // Reset frame sequence and set mode
        frameSequences.put(sessionId, 0L);
        useDOMSnapshot.put(sessionId, useDOMSnapshotMode);

        // Submit streaming task
        Future<?> streamingTask = executorService.submit(() -> streamFrames(session));
        activeStreams.put(sessionId, streamingTask);
    }

    /**
     * Stop streaming frames for a recorder session.
     *
     * @param sessionId recorder session ID
     */
    public void stopStreaming(UUID sessionId) {
        Future<?> streamingTask = activeStreams.remove(sessionId);

        if (streamingTask != null) {
            log.info("Stopping frame streaming for session: {}", sessionId);
            streamingTask.cancel(true);
            frameSequences.remove(sessionId);
        }
    }

    /**
     * Main streaming loop that captures and broadcasts frames.
     *
     * @param session recorder session
     */
    private void streamFrames(RecorderSession session) {
        UUID sessionId = session.getSessionId();
        UUID browserSessionId = session.getBrowserSessionId();
        int frameRate = session.getFrameRate();
        long frameIntervalMs = 1000 / frameRate; // Convert FPS to milliseconds

        log.info("Frame streaming started: sessionId={}, fps={}, interval={}ms",
                sessionId, frameRate, frameIntervalMs);

        try {
            while (!Thread.currentThread().isInterrupted() && session.isRecording()) {
                long frameStartTime = System.currentTimeMillis();

                try {
                    // Capture and broadcast frame
                    captureAndBroadcastFrame(session, browserSessionId);

                    // Calculate sleep time to maintain FPS
                    long elapsedTime = System.currentTimeMillis() - frameStartTime;
                    long sleepTime = frameIntervalMs - elapsedTime;

                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    } else {
                        // Frame capture took longer than interval (backpressure)
                        log.debug("Frame capture slow: took {}ms (target: {}ms)", elapsedTime, frameIntervalMs);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error capturing frame for session: {}", sessionId, e);
                    // Continue streaming despite errors
                }
            }
        } finally {
            log.info("Frame streaming stopped for session: {}", sessionId);
            activeStreams.remove(sessionId);
            frameSequences.remove(sessionId);
        }
    }

    /**
     * Capture a frame (DOM snapshot or screenshot) and broadcast it via WebSocket.
     *
     * @param session recorder session
     * @param browserSessionId browser session ID
     */
    private void captureAndBroadcastFrame(RecorderSession session, UUID browserSessionId) {
        UUID sessionId = session.getSessionId();
        boolean useDOMMode = useDOMSnapshot.getOrDefault(sessionId, true);

        try {
            // Get page from browser manager
            PageSession pageSession = browserManager.getSession(browserSessionId)
                    .orElseThrow(() -> new IllegalStateException("Browser session not found: " + browserSessionId));

            Page page = pageSession.page();

            // Check if page is closed
            if (page.isClosed()) {
                log.warn("Page is closed, stopping frame capture for session: {}", sessionId);
                stopStreaming(sessionId);
                return;
            }

            // Get and increment frame sequence
            long frameSequence = frameSequences.compute(sessionId, (k, v) -> (v == null ? 0 : v) + 1);

            if (useDOMMode) {
                // Capture DOM snapshot (lightweight, interactive)
                captureDOMSnapshotAndBroadcast(session, page, frameSequence);
            } else {
                // Fallback to screenshot mode
                captureScreenshotAndBroadcast(session, page, frameSequence);
            }

        } catch (com.microsoft.playwright.PlaywrightException e) {
            // Handle Playwright binding errors gracefully
            if (e.getMessage().contains("bindingCall") || e.getMessage().contains("Object doesn't exist")) {
                log.debug("Playwright binding error (transient), skipping frame: {}", e.getMessage());
                // Skip this frame and continue streaming
            } else {
                log.error("Playwright error capturing frame for session: {}", sessionId, e);
                throw e;
            }
        } catch (Exception e) {
            log.error("Error capturing frame for session: {}", sessionId, e);
            // Try to fall back to screenshot mode on next frame
            if (useDOMMode) {
                log.warn("DOM snapshot failed, falling back to screenshot mode for session: {}", sessionId);
                useDOMSnapshot.put(sessionId, false);
            }
        }
    }

    /**
     * Capture DOM snapshot and broadcast via WebSocket.
     */
    private void captureDOMSnapshotAndBroadcast(RecorderSession session, Page page, long frameSequence) {
        UUID sessionId = session.getSessionId();

        // Capture DOM snapshot
        DOMSnapshotData domSnapshot = domSnapshotService.captureDOMSnapshot(page);

        // Create DOM frame message
        DOMFrameMessage frameMessage = new DOMFrameMessage(
                frameSequence,
                domSnapshot.timestamp(),
                domSnapshot.url(),
                domSnapshot.domHTML(),
                domSnapshot.stylesheets(),
                domSnapshot.viewportWidth(),
                domSnapshot.viewportHeight(),
                domSnapshot.devicePixelRatio(),
                domSnapshot.scrollX(),
                domSnapshot.scrollY(),
                domSnapshot.sizeBytes(),
                "dom" // Frame type
        );

        // Broadcast to WebSocket topic
        String destination = "/topic/recorder/" + sessionId + "/frames";
        messagingTemplate.convertAndSend(destination, frameMessage);

        log.trace("DOM frame broadcasted: sessionId={}, sequence={}, size={}KB",
                sessionId, frameSequence, domSnapshot.sizeBytes() / 1024);
    }

    /**
     * Capture screenshot and broadcast via WebSocket (fallback mode).
     */
    private void captureScreenshotAndBroadcast(RecorderSession session, Page page, long frameSequence) {
        UUID sessionId = session.getSessionId();

        // Capture screenshot with options to avoid binding conflicts
        Page.ScreenshotOptions screenshotOptions = new Page.ScreenshotOptions()
                .setFullPage(false); // Capture viewport only for better performance

        byte[] screenshotBytes = page.screenshot(screenshotOptions);
        String screenshotBase64 = Base64.getEncoder().encodeToString(screenshotBytes);

        // Get current page URL safely
        String currentUrl;
        try {
            currentUrl = page.url();
        } catch (Exception e) {
            currentUrl = "unknown";
            log.debug("Could not get page URL: {}", e.getMessage());
        }

        // Create screenshot frame message
        ScreenshotFrameMessage frameMessage = new ScreenshotFrameMessage(
                frameSequence,
                System.currentTimeMillis(),
                screenshotBase64,
                currentUrl,
                screenshotBytes.length,
                "screenshot" // Frame type
        );

        // Broadcast to WebSocket topic
        String destination = "/topic/recorder/" + sessionId + "/frames";
        messagingTemplate.convertAndSend(destination, frameMessage);

        log.trace("Screenshot frame broadcasted: sessionId={}, sequence={}, size={}KB",
                sessionId, frameSequence, screenshotBytes.length / 1024);
    }

    /**
     * Check if streaming is active for a session.
     *
     * @param sessionId recorder session ID
     * @return true if streaming is active
     */
    public boolean isStreaming(UUID sessionId) {
        return activeStreams.containsKey(sessionId);
    }

    /**
     * Get count of active streaming sessions.
     *
     * @return number of active streams
     */
    public int getActiveStreamCount() {
        return activeStreams.size();
    }

    /**
     * DOM Frame message sent via WebSocket (new format).
     * Contains complete DOM structure with styles for interactive replay.
     *
     * @param sequence frame sequence number
     * @param timestamp Unix timestamp in milliseconds
     * @param url current page URL
     * @param domHTML Serialized HTML with computed styles
     * @param stylesheets External and inline CSS stylesheets
     * @param viewportWidth viewport width in pixels
     * @param viewportHeight viewport height in pixels
     * @param devicePixelRatio device pixel ratio
     * @param scrollX horizontal scroll position
     * @param scrollY vertical scroll position
     * @param sizeBytes approximate data size in bytes
     * @param type frame type ("dom")
     */
    public record DOMFrameMessage(
            long sequence,
            long timestamp,
            String url,
            String domHTML,
            java.util.List<String> stylesheets,
            int viewportWidth,
            int viewportHeight,
            double devicePixelRatio,
            double scrollX,
            double scrollY,
            int sizeBytes,
            String type
    ) {}

    /**
     * Screenshot Frame message sent via WebSocket (legacy/fallback format).
     *
     * @param sequence frame sequence number
     * @param timestamp Unix timestamp in milliseconds
     * @param imageData Base64-encoded PNG screenshot
     * @param url current page URL
     * @param sizeBytes screenshot size in bytes
     * @param type frame type ("screenshot")
     */
    public record ScreenshotFrameMessage(
            long sequence,
            long timestamp,
            String imageData,
            String url,
            int sizeBytes,
            String type
    ) {}

    /**
     * Legacy frame message for backward compatibility.
     * @deprecated Use DOMFrameMessage or ScreenshotFrameMessage instead.
     */
    @Deprecated
    public record FrameMessage(
            long sequence,
            long timestamp,
            String imageData,
            String url,
            int sizeBytes
    ) {}
}
