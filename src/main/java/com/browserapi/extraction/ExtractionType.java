package com.browserapi.extraction;

/**
 * Types of content extraction supported by the Browser API.
 * Each type represents a different extraction strategy.
 */
public enum ExtractionType {

    /**
     * Extract HTML content from page elements.
     * Returns raw HTML as string (innerHTML or outerHTML).
     * <p>
     * Use cases:
     * - Scraping page content
     * - Extracting article text
     * - Getting element structure
     */
    HTML,

    /**
     * Extract CSS styles from page elements.
     * Returns computed styles and stylesheet rules as CSS string or JSON.
     * <p>
     * Use cases:
     * - Extracting component styles
     * - Analyzing page design
     * - Style auditing
     */
    CSS,

    /**
     * Extract page data as structured JSON.
     * Converts DOM elements to JSON objects/arrays using schema mapping.
     * <p>
     * Use cases:
     * - Converting tables to JSON
     * - Extracting product listings
     * - Structured data extraction
     */
    JSON
}
