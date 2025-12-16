package com.browserapi.js.model;

import java.util.List;

/**
 * Result of JavaScript encapsulation operation.
 * Contains the original code and the encapsulated version with scope isolation.
 */
public record EncapsulatedJavaScript(
        String originalCode,
        String encapsulatedCode,
        String rootElementVariable,
        List<String> scopeReplacements,
        boolean hasAsyncCode,
        EncapsulationType encapsulationType
) {
    public EncapsulatedJavaScript(
            String originalCode,
            String encapsulatedCode,
            String rootElementVariable,
            List<String> scopeReplacements,
            boolean hasAsyncCode
    ) {
        this(
                originalCode,
                encapsulatedCode,
                rootElementVariable,
                scopeReplacements,
                hasAsyncCode,
                EncapsulationType.IIFE
        );
    }

    public enum EncapsulationType {
        IIFE,           // Immediately Invoked Function Expression
        MODULE,         // ES6 Module
        CLOSURE         // Closure with explicit context
    }

    public boolean isEncapsulated() {
        return !originalCode.equals(encapsulatedCode);
    }
}
