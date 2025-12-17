package com.browserapi.component.model;

/**
 * Types of iframe embed codes.
 */
public enum EmbedType {
    /**
     * Fixed size iframe with explicit width and height.
     */
    FIXED,

    /**
     * Responsive iframe that adapts to container width.
     * Uses aspect ratio padding technique.
     */
    RESPONSIVE,

    /**
     * Minimal iframe with just the source URL.
     * User is responsible for sizing.
     */
    MINIMAL
}
