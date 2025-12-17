package com.browserapi.component.model;

import java.util.Map;

/**
 * Result of exporting a component to a specific format.
 */
public record ComponentExport(
        ExportFormat format,
        Map<String, String> files,
        String mainFile,
        String usageInstructions
) {
    /**
     * Creates an export with a single file.
     */
    public static ComponentExport single(ExportFormat format, String filename, String content, String usage) {
        return new ComponentExport(
                format,
                Map.of(filename, content),
                filename,
                usage
        );
    }

    /**
     * Creates an export with multiple files.
     */
    public static ComponentExport multiple(
            ExportFormat format,
            Map<String, String> files,
            String mainFile,
            String usage
    ) {
        return new ComponentExport(format, files, mainFile, usage);
    }

    /**
     * Gets the content of the main file.
     */
    public String getMainContent() {
        return files.get(mainFile);
    }

    /**
     * Returns true if this export has multiple files.
     */
    public boolean hasMultipleFiles() {
        return files.size() > 1;
    }
}
