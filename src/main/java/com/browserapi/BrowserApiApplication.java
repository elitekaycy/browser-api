package com.browserapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Browser API service.
 * Provides web scraping and component extraction as a REST API using Playwright.
 */
@SpringBootApplication
public class BrowserApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BrowserApiApplication.class, args);
    }
}
