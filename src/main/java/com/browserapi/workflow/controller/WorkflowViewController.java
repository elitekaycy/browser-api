package com.browserapi.workflow.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for serving the workflows management UI.
 * Provides a visual interface for browsing, searching, editing, and executing workflows.
 */
@Controller
@RequestMapping("/workflows")
public class WorkflowViewController {

    /**
     * Serve the workflows management HTML page.
     *
     * @param model Spring MVC model for passing data to view
     * @return Thymeleaf template name
     */
    @GetMapping
    public String workflowsPage(Model model) {
        model.addAttribute("appName", "Workflow Manager");
        model.addAttribute("apiBaseUrl", "/api/v1");
        return "workflows";
    }
}
