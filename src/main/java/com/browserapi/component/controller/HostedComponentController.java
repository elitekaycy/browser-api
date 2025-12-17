package com.browserapi.component.controller;

import com.browserapi.component.entity.ComponentFile;
import com.browserapi.component.service.ComponentHostingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Controller for serving hosted component files.
 * Handles GET requests to publicly accessible component URLs.
 * CORS enabled to allow iframe embedding from any origin.
 */
@RestController
@RequestMapping("/hosted")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Tag(name = "Hosted Components", description = "Access hosted component files")
public class HostedComponentController {

    private static final Logger log = LoggerFactory.getLogger(HostedComponentController.class);

    private final ComponentHostingService hostingService;

    public HostedComponentController(ComponentHostingService hostingService) {
        this.hostingService = hostingService;
    }

    @GetMapping("/{fileId}.html")
    @Operation(
            summary = "Retrieve a hosted component file",
            description = """
                    Serves a hosted component HTML file by its unique file ID.

                    - Automatically tracks view count
                    - Returns 404 if file not found or expired
                    - Returns pure HTML content (Content-Type: text/html)

                    Example: GET /hosted/abc123.html
                    """
    )
    public ResponseEntity<String> getHostedComponent(@PathVariable String fileId) {
        log.info("Hosted component request: fileId={}", fileId);

        try {
            Optional<ComponentFile> componentFile = hostingService.getHostedComponent(fileId);

            if (componentFile.isEmpty()) {
                log.warn("Hosted component not found or expired: fileId={}", fileId);
                return ResponseEntity.notFound().build();
            }

            ComponentFile file = componentFile.get();

            // Read file content
            String htmlContent = hostingService.readFileContent(file);

            log.info("Served hosted component: fileId={}, viewCount={}", fileId, file.getViewCount());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600") // Cache for 1 hour
                    .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*") // Allow CORS
                    .header("X-Frame-Options", "ALLOWALL") // Allow iframe embedding
                    .body(htmlContent);

        } catch (Exception e) {
            log.error("Failed to serve hosted component: fileId={}", fileId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{fileId}")
    @Operation(
            summary = "Delete a hosted component file",
            description = """
                    Deletes a hosted component file before its expiration.

                    Returns:
                    - 200 OK if deleted successfully
                    - 404 Not Found if file doesn't exist
                    """
    )
    public ResponseEntity<?> deleteHostedComponent(@PathVariable String fileId) {
        log.info("Delete hosted component request: fileId={}", fileId);

        try {
            boolean deleted = hostingService.deleteHostedComponent(fileId);

            if (deleted) {
                log.info("Hosted component deleted: fileId={}", fileId);
                return ResponseEntity.ok().body("Hosted component deleted successfully");
            } else {
                log.warn("Hosted component not found: fileId={}", fileId);
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("Failed to delete hosted component: fileId={}", fileId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
