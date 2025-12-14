package com.browserapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for Browser API documentation.
 * Provides interactive API documentation accessible at /swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Value("${browser-api.version}")
    private String appVersion;

    @Value("${server.port}")
    private int serverPort;

    @Bean
    public OpenAPI browserApiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Browser-as-API Service")
                        .description("""
                                **Browser-as-API Service** - Extract web components, content, and automate browser actions using Playwright.

                                ## Features
                                - Extract HTML, CSS, JSON from any webpage
                                - Extract isolated, reusable web components (Shadow DOM, Web Components)
                                - Execute browser actions (click, type, navigate)
                                - Create and execute workflows
                                - Record user interactions
                                - Built-in caching for performance

                                ## Getting Started
                                1. Use `/api/v1/{projectName}/extract` for simple content extraction
                                2. Use `/api/v1/{projectName}/component` for component extraction
                                3. Use `/api/v1/{projectName}/actions/execute` for browser automation

                                ## Authentication
                                Currently no authentication required. Rate limiting may be added in future versions.
                                """)
                        .version(appVersion)
                        .contact(new Contact()
                                .name("Browser API Support")
                                .url("https://github.com/yourname/browser-api")
                                .email("support@browserapi.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local development server"),
                        new Server()
                                .url("http://localhost:" + serverPort + "/api/v1")
                                .description("API v1 base path")
                ));
    }
}
