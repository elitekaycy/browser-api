package com.browserapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Browser API service.
 * Provides web scraping and component extraction as a REST API using Playwright.
 */
@SpringBootApplication
@EnableScheduling
public class BrowserApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BrowserApiApplication.class, args);
    }
}
