package com.browserapi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Main configuration properties for the Browser API service.
 * Binds to properties prefixed with "browser-api" in application.yml.
 */
@Configuration
@ConfigurationProperties(prefix = "browser-api")
@Getter
@Setter
public class ProjectConfig {

    private String projectName;
    private String version;
    private String basePath;
    private BrowserConfig browser = new BrowserConfig();
    private CacheConfig cache = new CacheConfig();

    @Getter
    @Setter
    public static class BrowserConfig {
        private boolean headless = true;
        private int timeoutMs = 30000;
        private int maxSessions = 10;
        private int sessionIdleTimeoutMs = 300000;
    }

    @Getter
    @Setter
    public static class CacheConfig {
        private boolean enabled = true;
        private int defaultTtlSeconds = 3600;
        private int componentTtlSeconds = 86400;
        private int maxSizeMb = 100;
    }
}
