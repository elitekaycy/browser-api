package com.browserapi.component.service;

import com.browserapi.component.entity.ComponentFile;
import com.browserapi.component.model.CompleteComponent;
import com.browserapi.component.repository.ComponentFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for hosting component files as static HTML.
 * <p>
 * Features:
 * <ul>
 *   <li>Save components as HTML files with unique URLs</li>
 *   <li>Track view counts and access statistics</li>
 *   <li>Auto-expiration based on TTL</li>
 *   <li>Cleanup of expired files</li>
 * </ul>
 */
@Service
public class ComponentHostingService {

    private static final Logger log = LoggerFactory.getLogger(ComponentHostingService.class);
    private static final int DEFAULT_TTL_HOURS = 24;

    private final ComponentFileRepository repository;

    @Value("${component.hosting.base-dir:./hosted-components}")
    private String baseDirectory;

    @Value("${component.hosting.base-url:/hosted}")
    private String baseUrl;

    @Value("${component.hosting.ttl-hours:24}")
    private int ttlHours;

    public ComponentHostingService(ComponentFileRepository repository) {
        this.repository = repository;
    }

    /**
     * Hosts a component as a static HTML file with a unique shareable URL.
     *
     * @param url source URL
     * @param selector CSS selector
     * @param component component to host
     * @return hosted file information
     */
    @Transactional
    public ComponentFile hostComponent(String url, String selector, CompleteComponent component) {
        try {
            // Ensure directory exists
            Path dirPath = Paths.get(baseDirectory);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                log.info("Created hosting directory: {}", baseDirectory);
            }

            // Generate unique file ID
            String fileId = generateFileId(url, selector, component.namespace());

            // Check if already hosted
            Optional<ComponentFile> existing = repository.findByFileId(fileId);
            if (existing.isPresent() && !existing.get().isExpired()) {
                log.info("Component already hosted: fileId={}", fileId);
                return existing.get();
            }

            // Generate HTML content
            String htmlContent = component.toHTML();

            // Save to file
            String filename = fileId + ".html";
            Path filePath = dirPath.resolve(filename);
            Files.writeString(filePath, htmlContent);

            long fileSizeBytes = Files.size(filePath);

            // Create database record
            ComponentFile componentFile = new ComponentFile();
            componentFile.setFileId(fileId);
            componentFile.setUrl(url);
            componentFile.setSelector(selector);
            componentFile.setFilePath("hosted-components/" + filename);
            componentFile.setPublicUrl(baseUrl + "/" + filename);
            componentFile.setFileSizeBytes(fileSizeBytes);
            componentFile.setExpiresAt(LocalDateTime.now().plusHours(ttlHours));
            componentFile.setNamespace(component.namespace());
            componentFile.setFormat("HTML");

            repository.save(componentFile);

            log.info("Component hosted: fileId={}, url={}, size={}KB, publicUrl={}",
                    fileId, componentFile.getPublicUrl(), fileSizeBytes / 1024, componentFile.getPublicUrl());

            return componentFile;

        } catch (IOException e) {
            log.error("Failed to host component: url={}, selector={}", url, selector, e);
            throw new RuntimeException("Failed to host component: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a hosted component file by its file ID.
     * Increments view count and updates last viewed timestamp.
     *
     * @param fileId the file ID
     * @return component file if found and not expired
     */
    @Transactional
    public Optional<ComponentFile> getHostedComponent(String fileId) {
        Optional<ComponentFile> componentFile = repository
                .findByFileIdAndExpiresAtAfter(fileId, LocalDateTime.now());

        if (componentFile.isPresent()) {
            ComponentFile file = componentFile.get();
            file.incrementViewCount();
            repository.save(file);

            log.info("Component file accessed: fileId={}, viewCount={}", fileId, file.getViewCount());
        }

        return componentFile;
    }

    /**
     * Reads the HTML content of a hosted component file.
     *
     * @param componentFile the component file record
     * @return HTML content
     */
    public String readFileContent(ComponentFile componentFile) {
        try {
            Path filePath = Paths.get(baseDirectory, componentFile.getFileId() + ".html");
            return Files.readString(filePath);
        } catch (IOException e) {
            log.error("Failed to read component file: fileId={}", componentFile.getFileId(), e);
            throw new RuntimeException("Failed to read component file: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a hosted component file.
     *
     * @param fileId the file ID
     * @return true if deleted, false if not found
     */
    @Transactional
    public boolean deleteHostedComponent(String fileId) {
        Optional<ComponentFile> componentFile = repository.findByFileId(fileId);

        if (componentFile.isEmpty()) {
            return false;
        }

        ComponentFile file = componentFile.get();

        // Delete physical file
        try {
            Path filePath = Paths.get(baseDirectory, file.getFileId() + ".html");
            Files.deleteIfExists(filePath);
            log.info("Deleted physical file: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to delete physical file: fileId={}", fileId, e);
        }

        // Delete database record
        repository.delete(file);
        log.info("Deleted component file record: fileId={}", fileId);

        return true;
    }

    /**
     * Cleans up expired component files.
     * Removes both physical files and database records.
     *
     * @return number of files deleted
     */
    @Transactional
    public int cleanupExpiredFiles() {
        log.debug("Starting cleanup of expired component files");

        List<ComponentFile> expiredFiles = repository.findExpired(LocalDateTime.now());

        int deletedCount = 0;

        for (ComponentFile file : expiredFiles) {
            // Delete physical file
            try {
                Path filePath = Paths.get(baseDirectory, file.getFileId() + ".html");
                if (Files.deleteIfExists(filePath)) {
                    deletedCount++;
                    log.debug("Deleted expired file: fileId={}", file.getFileId());
                }
            } catch (IOException e) {
                log.error("Failed to delete expired file: fileId={}", file.getFileId(), e);
            }
        }

        // Delete database records
        int dbDeleted = repository.deleteExpired(LocalDateTime.now());

        log.info("Cleanup completed: deleted {} physical files, {} database records", deletedCount, dbDeleted);

        return deletedCount;
    }

    /**
     * Gets hosting statistics.
     *
     * @return map of metric name to value
     */
    public Map<String, Object> getStatistics() {
        long totalFiles = repository.count();
        long totalViews = repository.sumAllViewCounts();
        long totalSizeBytes = repository.sumAllFileSizes();
        long expiredCount = repository.countExpired(LocalDateTime.now());

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFiles", totalFiles);
        stats.put("totalViews", totalViews);
        stats.put("totalSizeMB", totalSizeBytes / 1024.0 / 1024.0);
        stats.put("expiredCount", expiredCount);
        stats.put("htmlCount", repository.countByFormat("HTML"));

        return stats;
    }

    /**
     * Generates a unique file ID from URL, selector, and namespace.
     *
     * @param url source URL
     * @param selector CSS selector
     * @param namespace component namespace
     * @return unique file ID (8 characters)
     */
    private String generateFileId(String url, String selector, String namespace) {
        String input = url + "|" + selector + "|" + namespace + "|" + System.currentTimeMillis();

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 4; i++) { // Use first 4 bytes for 8-character ID
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to generate file ID", e);
            return Integer.toHexString(input.hashCode());
        }
    }
}
