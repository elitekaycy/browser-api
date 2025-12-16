package com.browserapi.cache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Configuration properties for caching behavior.
 * Binds to application.yml cache settings.
 */
@Configuration
@ConfigurationProperties(prefix = "browser-api.cache")
public class CacheConfig {

    private boolean enabled = true;
    private int defaultTtlSeconds = 3600;
    private int componentTtlSeconds = 86400;
    private int maxSizeMb = 100;
    private Map<String, Integer> ttl;

    /**
     * Gets TTL in seconds for a specific extraction type.
     * Falls back to default if type not configured.
     *
     * @param extractionType HTML, CSS, or JSON
     * @return TTL in seconds
     */
    public int getTtlForType(String extractionType) {
        if (ttl != null && ttl.containsKey(extractionType.toLowerCase())) {
            return ttl.get(extractionType.toLowerCase());
        }
        return defaultTtlSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDefaultTtlSeconds() {
        return defaultTtlSeconds;
    }

    public void setDefaultTtlSeconds(int defaultTtlSeconds) {
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    public int getComponentTtlSeconds() {
        return componentTtlSeconds;
    }

    public void setComponentTtlSeconds(int componentTtlSeconds) {
        this.componentTtlSeconds = componentTtlSeconds;
    }

    public int getMaxSizeMb() {
        return maxSizeMb;
    }

    public void setMaxSizeMb(int maxSizeMb) {
        this.maxSizeMb = maxSizeMb;
    }

    public Map<String, Integer> getTtl() {
        return ttl;
    }

    public void setTtl(Map<String, Integer> ttl) {
        this.ttl = ttl;
    }
}
