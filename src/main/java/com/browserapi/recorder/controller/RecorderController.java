package com.browserapi.recorder.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for serving the browser action recorder UI.
 */
@Controller
@RequestMapping("/recorder")
public class RecorderController {

    /**
     * Serve the recorder HTML page.
     *
     * @param model Spring MVC model for passing data to view
     * @return Thymeleaf template name
     */
    @GetMapping
    public String recorderPage(Model model) {
        // Add any necessary model attributes
        model.addAttribute("appName", "Browser Action Recorder");
        model.addAttribute("version", "1.0.0");
        model.addAttribute("apiBaseUrl", "/api/v1");

        return "recorder";
    }
}
