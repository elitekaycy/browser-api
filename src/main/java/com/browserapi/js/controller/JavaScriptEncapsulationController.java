package com.browserapi.js.controller;

import com.browserapi.js.model.EncapsulatedJavaScript;
import com.browserapi.js.service.JavaScriptEncapsulator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API controller for JavaScript encapsulation operations.
 * Provides endpoints to encapsulate JavaScript code for component isolation.
 */
@RestController
@RequestMapping("/api/v1/js/encapsulate")
@Tag(name = "JavaScript Encapsulation", description = "Encapsulate JavaScript to prevent naming conflicts")
public class JavaScriptEncapsulationController {

    private static final Logger log = LoggerFactory.getLogger(JavaScriptEncapsulationController.class);

    private final JavaScriptEncapsulator jsEncapsulator;

    public JavaScriptEncapsulationController(JavaScriptEncapsulator jsEncapsulator) {
        this.jsEncapsulator = jsEncapsulator;
    }

    @PostMapping
    @Operation(
            summary = "Encapsulate JavaScript code",
            description = """
                    Encapsulates JavaScript code to prevent naming conflicts and scope pollution:
                    - Wraps code in IIFE (Immediately Invoked Function Expression)
                    - Rewrites document queries to be scoped to root element
                    - Preserves async/await functionality
                    - Maintains context and this binding

                    Example request body:
                    {
                      "code": "var counter = 0; document.querySelector('.btn').onclick = () => counter++;",
                      "rootVariable": "rootElement",
                      "encapsulationType": "IIFE"
                    }

                    Encapsulation types:
                    - IIFE: Immediately Invoked Function Expression (default)
                    - MODULE: ES6 Module structure
                    - CLOSURE: Closure with explicit context
                    """
    )
    public ResponseEntity<?> encapsulate(@RequestBody EncapsulationRequest request) {
        log.info("JavaScript encapsulation request: type={}, codeLength={}",
                request.encapsulationType(), request.code().length());

        try {
            String rootVar = request.rootVariable() != null
                    ? request.rootVariable()
                    : "rootElement";

            EncapsulatedJavaScript.EncapsulationType type =
                    request.encapsulationType() != null
                            ? request.encapsulationType()
                            : EncapsulatedJavaScript.EncapsulationType.IIFE;

            EncapsulatedJavaScript result = jsEncapsulator.encapsulate(
                    request.code(),
                    rootVar,
                    type
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("JavaScript encapsulation failed", e);
            return ResponseEntity
                    .internalServerError()
                    .body(Map.of("error", "JavaScript encapsulation failed", "message", e.getMessage()));
        }
    }

    @PostMapping("/code")
    @Operation(
            summary = "Encapsulate JavaScript code (returns only encapsulated code)",
            description = "Same as /encapsulate but returns only the encapsulated code as plain text"
    )
    public ResponseEntity<String> encapsulateCode(@RequestBody EncapsulationRequest request) {
        log.info("JavaScript encapsulation (code only) request: type={}, codeLength={}",
                request.encapsulationType(), request.code().length());

        try {
            String rootVar = request.rootVariable() != null
                    ? request.rootVariable()
                    : "rootElement";

            EncapsulatedJavaScript.EncapsulationType type =
                    request.encapsulationType() != null
                            ? request.encapsulationType()
                            : EncapsulatedJavaScript.EncapsulationType.IIFE;

            EncapsulatedJavaScript result = jsEncapsulator.encapsulate(
                    request.code(),
                    rootVar,
                    type
            );

            return ResponseEntity
                    .ok()
                    .header("Content-Type", "application/javascript")
                    .body(result.encapsulatedCode());

        } catch (Exception e) {
            log.error("JavaScript encapsulation failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    public record EncapsulationRequest(
            String code,
            String rootVariable,
            EncapsulatedJavaScript.EncapsulationType encapsulationType
    ) {
    }
}
