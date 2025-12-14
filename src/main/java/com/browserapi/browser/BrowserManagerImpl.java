package com.browserapi.browser;

import com.browserapi.config.ProjectConfig;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of BrowserManager using Playwright.
 * Manages a singleton Playwright instance and browser with multiple page sessions.
 *
 * <p>Thread-safety: All public methods are thread-safe.
 * <p>Resource management: Automatically cleans up idle sessions and shuts down gracefully.
 */
@Service
public class BrowserManagerImpl implements BrowserManager, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(BrowserManagerImpl.class);

    private final ProjectConfig config;
    private final ConcurrentHashMap<UUID, PageSession> activeSessions = new ConcurrentHashMap<>();

    private Playwright playwright;
    private Browser browser;

    public BrowserManagerImpl(ProjectConfig config) {
        this.config = config;
    }

    /**
     * Initializes Playwright and browser on application startup.
     * Called automatically by Spring after bean construction.
     */
    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing Playwright browser manager...");

            playwright = Playwright.create();
            log.info("Playwright instance created");

            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(config.getBrowser().isHeadless())
                    .setTimeout(config.getBrowser().getTimeoutMs());

            browser = playwright.chromium().launch(launchOptions);
            log.info("Browser launched (headless: {}, timeout: {}ms)",
                    config.getBrowser().isHeadless(),
                    config.getBrowser().getTimeoutMs());

        } catch (Exception e) {
            log.error("Failed to initialize browser manager", e);
            throw new BrowserException("Failed to initialize Playwright browser", e);
        }
    }

    @Override
    public PageSession createSession(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be null or blank");
        }

        // Enforce session limit
        int currentSessions = activeSessions.size();
        int maxSessions = config.getBrowser().getMaxSessions();
        if (currentSessions >= maxSessions) {
            throw new BrowserException(
                    "Maximum sessions limit reached: %d/%d. Close existing sessions or increase limit."
                            .formatted(currentSessions, maxSessions)
            );
        }

        try {
            log.debug("Creating new browser session for URL: {}", url);

            // Create new page with timeout
            Page page = browser.newPage();
            page.setDefaultTimeout(config.getBrowser().getTimeoutMs());

            // Navigate to URL with wait for load state
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(
                    com.microsoft.playwright.options.WaitUntilState.LOAD
            ));

            PageSession session = new PageSession(page, url);
            activeSessions.put(session.sessionId(), session);

            log.info("Browser session created: {} (active: {}/{})",
                    session.sessionId(),
                    activeSessions.size(),
                    maxSessions);

            return session;

        } catch (Exception e) {
            log.error("Failed to create browser session for URL: {}", url, e);
            throw new BrowserException("Failed to create browser session for: " + url, e);
        }
    }

    @Override
    public void closeSession(UUID sessionId) {
        if (sessionId == null) {
            return;
        }

        PageSession session = activeSessions.remove(sessionId);
        if (session != null) {
            try {
                session.close();
                log.debug("Browser session closed: {} (active: {})",
                        sessionId,
                        activeSessions.size());
            } catch (Exception e) {
                log.warn("Error closing session {}: {}", sessionId, e.getMessage());
            }
        }
    }

    @Override
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Scheduled cleanup of idle sessions.
     * Runs every 60 seconds to check for sessions exceeding idle timeout.
     */
    @Scheduled(fixedRate = 60000) // Every 60 seconds
    public void cleanupIdleSessions() {
        long idleTimeout = config.getBrowser().getSessionIdleTimeoutMs();

        List<UUID> idleSessions = activeSessions.values().stream()
                .filter(session -> session.isIdleFor(idleTimeout))
                .map(PageSession::sessionId)
                .toList();

        if (!idleSessions.isEmpty()) {
            log.info("Cleaning up {} idle sessions (timeout: {}ms)", idleSessions.size(), idleTimeout);
            idleSessions.forEach(this::closeSession);
        }
    }

    @Override
    public void shutdown() {
        log.info("Shutting down browser manager...");

        // Close all active sessions
        int sessionCount = activeSessions.size();
        if (sessionCount > 0) {
            log.info("Closing {} active sessions", sessionCount);
            activeSessions.keySet().forEach(this::closeSession);
        }

        // Close browser and Playwright
        if (browser != null) {
            try {
                browser.close();
                log.info("Browser closed");
            } catch (Exception e) {
                log.warn("Error closing browser: {}", e.getMessage());
            }
        }

        if (playwright != null) {
            try {
                playwright.close();
                log.info("Playwright closed");
            } catch (Exception e) {
                log.warn("Error closing Playwright: {}", e.getMessage());
            }
        }

        log.info("Browser manager shutdown complete");
    }

    /**
     * Spring DisposableBean callback for graceful shutdown.
     * Ensures resources are cleaned up when application stops.
     */
    @Override
    @PreDestroy
    public void destroy() {
        shutdown();
    }
}
