package com.browserapi.browser;

import com.browserapi.config.ProjectConfig;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
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

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    private final NavigationHelper navigationHelper;
    private final ConcurrentHashMap<UUID, PageSession> activeSessions = new ConcurrentHashMap<>();

    private Playwright playwright;
    private Browser browser;
    private BrowserContext defaultContext;

    public BrowserManagerImpl(ProjectConfig config, NavigationHelper navigationHelper) {
        this.config = config;
        this.navigationHelper = navigationHelper;
    }

    /**
     * Initializes Playwright and browser on application startup.
     * Called automatically by Spring after bean construction.
     * Configures stealth mode and anti-bot detection features.
     */
    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing Playwright browser manager with anti-detection...");

            playwright = Playwright.create();
            log.info("Playwright instance created");

            // Configure browser launch with stealth options
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(config.getBrowser().isHeadless())
                    .setTimeout(config.getBrowser().getTimeoutMs())
                    .setArgs(Arrays.asList(
                        // Disable automation flags
                        "--disable-blink-features=AutomationControlled",
                        // Enable features that make it look like a real browser
                        "--disable-dev-shm-usage",
                        "--disable-setuid-sandbox",
                        "--no-sandbox",
                        // GPU and rendering
                        "--disable-gpu",
                        "--disable-software-rasterizer",
                        // Window size
                        "--window-size=1920,1080",
                        // Additional flags for normal browser behavior
                        "--disable-background-timer-throttling",
                        "--disable-backgrounding-occluded-windows",
                        "--disable-renderer-backgrounding",
                        // Enable cookies and storage
                        "--enable-features=NetworkService,NetworkServiceInProcess"
                    ));

            browser = playwright.chromium().launch(launchOptions);
            log.info("Browser launched (headless: {}, timeout: {}ms, stealth: enabled)",
                    config.getBrowser().isHeadless(),
                    config.getBrowser().getTimeoutMs());

            // Create a default browser context with stealth configuration
            createDefaultContext();

        } catch (Exception e) {
            log.error("Failed to initialize browser manager", e);
            throw new BrowserException("Failed to initialize Playwright browser", e);
        }
    }

    /**
     * Creates a browser context with stealth configuration and anti-detection features.
     * This enables JavaScript, cookies, and removes automation indicators.
     */
    private void createDefaultContext() {
        try {
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                    // Set realistic viewport
                    .setViewportSize(1920, 1080)
                    // Set realistic user agent (Chrome on Windows)
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    // Accept downloads
                    .setAcceptDownloads(true)
                    // Enable JavaScript (default is true, but explicit)
                    .setJavaScriptEnabled(true)
                    // Enable cookies and local storage
                    .setStorageState(null)  // Fresh state
                    // Set locale and timezone
                    .setLocale("en-US")
                    .setTimezoneId("America/New_York")
                    // Set permissions for notifications, geolocation, etc.
                    .setPermissions(Arrays.asList("geolocation", "notifications"))
                    // Set geolocation
                    .setGeolocation(40.7128, -74.0060)  // New York City
                    // Color scheme
                    .setColorScheme(com.microsoft.playwright.options.ColorScheme.LIGHT)
                    // Enable strict selectors for better reliability
                    .setStrictSelectors(false)
                    // Bypass CSP if needed
                    .setBypassCSP(true)
                    // Set screen size
                    .setScreenSize(1920, 1080)
                    // Device scale factor
                    .setDeviceScaleFactor(1.0)
                    // Has touch (false for desktop)
                    .setHasTouch(false)
                    // Is mobile
                    .setIsMobile(false)
                    // Extra HTTP headers to look more like a real browser
                    .setExtraHTTPHeaders(Map.of(
                        "Accept-Language", "en-US,en;q=0.9",
                        "Accept-Encoding", "gzip, deflate, br",
                        "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                        "Sec-Fetch-Dest", "document",
                        "Sec-Fetch-Mode", "navigate",
                        "Sec-Fetch-Site", "none",
                        "Sec-Fetch-User", "?1",
                        "Upgrade-Insecure-Requests", "1"
                    ));

            defaultContext = browser.newContext(contextOptions);

            // CRITICAL: Remove navigator.webdriver property via script injection
            // This is the #1 indicator that automation frameworks check for
            defaultContext.addInitScript("""
                Object.defineProperty(navigator, 'webdriver', {
                    get: () => undefined
                });

                // Override the navigator.plugins to show realistic plugins
                Object.defineProperty(navigator, 'plugins', {
                    get: () => [
                        {
                            0: {type: "application/pdf", suffixes: "pdf", description: "Portable Document Format"},
                            name: "Chrome PDF Plugin",
                            description: "Portable Document Format",
                            filename: "internal-pdf-viewer"
                        },
                        {
                            0: {type: "application/x-google-chrome-pdf", suffixes: "pdf", description: "Portable Document Format"},
                            name: "Chrome PDF Viewer",
                            description: "Portable Document Format",
                            filename: "mhjfbmdgcfjbbpaeojofohoefgiehjai"
                        },
                        {
                            0: {type: "application/x-nacl", suffixes: "", description: "Native Client Executable"},
                            name: "Native Client",
                            description: "Native Client Executable",
                            filename: "internal-nacl-plugin"
                        }
                    ]
                });

                // Override permissions API to avoid detection
                const originalQuery = window.navigator.permissions.query;
                window.navigator.permissions.query = (parameters) => (
                    parameters.name === 'notifications' ?
                        Promise.resolve({ state: Notification.permission }) :
                        originalQuery(parameters)
                );

                // Make chrome object look realistic
                window.chrome = {
                    runtime: {},
                    loadTimes: function() {},
                    csi: function() {},
                    app: {}
                };

                // Override the automation-related properties
                Object.defineProperty(navigator, 'maxTouchPoints', {get: () => 0});
                Object.defineProperty(navigator, 'hardwareConcurrency', {get: () => 8});
                Object.defineProperty(navigator, 'deviceMemory', {get: () => 8});

                console.log('[Stealth] Anti-detection initialized');
            """);

            log.info("Browser context created with stealth configuration (JS: enabled, Cookies: enabled, WebDriver: hidden)");

        } catch (Exception e) {
            log.error("Failed to create browser context", e);
            throw new BrowserException("Failed to create browser context", e);
        }
    }

    @Override
    public PageSession createSession(String url) {
        return createSession(url, WaitStrategy.LOAD, null);
    }

    @Override
    public PageSession createSession(String url, WaitStrategy waitStrategy) {
        return createSession(url, waitStrategy, null);
    }

    @Override
    public PageSession createSession(String url, WaitStrategy waitStrategy, Long timeoutMs) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be null or blank");
        }
        if (waitStrategy == null) {
            waitStrategy = WaitStrategy.LOAD;
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
            log.debug("Creating new browser session for URL: {} (strategy: {}, timeout: {}ms)",
                    url, waitStrategy, timeoutMs != null ? timeoutMs : "default");

            // Create new page from the stealth-configured context
            // This inherits all anti-detection features and cookie/JS settings
            Page page = defaultContext.newPage();
            page.setDefaultTimeout(timeoutMs != null ? timeoutMs : config.getBrowser().getTimeoutMs());

            // Navigate using NavigationHelper with retry logic
            navigationHelper.navigateWithRetry(page, url, waitStrategy, timeoutMs);

            PageSession session = new PageSession(page, url);
            activeSessions.put(session.sessionId(), session);

            log.info("Browser session created: {} (active: {}/{}, strategy: {}, stealth: enabled)",
                    session.sessionId(),
                    activeSessions.size(),
                    maxSessions,
                    waitStrategy);

            return session;

        } catch (BrowserException e) {
            // NavigationHelper already provides detailed error messages
            throw e;
        } catch (Exception e) {
            log.error("Failed to create browser session for URL: {}", url, e);
            throw new BrowserException("Failed to create browser session for: " + url, e);
        }
    }

    @Override
    public java.util.Optional<PageSession> getSession(UUID sessionId) {
        if (sessionId == null) {
            return java.util.Optional.empty();
        }

        PageSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.touch(); // Update last accessed timestamp
        }
        return java.util.Optional.ofNullable(session);
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

        // Close browser context
        if (defaultContext != null) {
            try {
                defaultContext.close();
                log.info("Browser context closed");
            } catch (Exception e) {
                log.warn("Error closing browser context: {}", e.getMessage());
            }
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
