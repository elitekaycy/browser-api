package com.browserapi.component.model;

import java.util.List;

/**
 * Result of batch component extraction.
 */
public record BatchExtractionResult(
        List<BatchComponentResult> components,
        BatchExtractionSummary summary
) {
    public static class BatchExtractionSummary {
        private final int total;
        private final int successful;
        private final int failed;
        private final long totalExtractionTimeMs;

        public BatchExtractionSummary(
                int total,
                int successful,
                int failed,
                long totalExtractionTimeMs
        ) {
            this.total = total;
            this.successful = successful;
            this.failed = failed;
            this.totalExtractionTimeMs = totalExtractionTimeMs;
        }

        public int getTotal() {
            return total;
        }

        public int getSuccessful() {
            return successful;
        }

        public int getFailed() {
            return failed;
        }

        public long getTotalExtractionTimeMs() {
            return totalExtractionTimeMs;
        }
    }

    /**
     * Creates a batch result from components list.
     */
    public static BatchExtractionResult from(List<BatchComponentResult> components, long totalTimeMs) {
        int total = components.size();
        int successful = (int) components.stream().filter(BatchComponentResult::success).count();
        int failed = total - successful;

        BatchExtractionSummary summary = new BatchExtractionSummary(
                total,
                successful,
                failed,
                totalTimeMs
        );

        return new BatchExtractionResult(components, summary);
    }
}
