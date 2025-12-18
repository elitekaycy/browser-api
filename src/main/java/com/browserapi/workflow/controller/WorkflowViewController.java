package com.browserapi.workflow.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for serving the workflows management UI.
 * Provides a visual interface for browsing, searching, editing, and executing workflows.
 */
@Controller
public class WorkflowViewController {

    /**
     * Serve the workflows management HTML page.
     *
     * @param model Spring MVC model for passing data to view
     * @return Thymeleaf template name
     */
    @GetMapping("/workflows")
    public String workflowsPage(Model model) {
        model.addAttribute("appName", "Workflow Manager");
        model.addAttribute("apiBaseUrl", "/api/v1");
        model.addAttribute("view", "all");
        return "workflows";
    }

    @GetMapping("/workflows/most-executed")
    public String mostExecuted(Model model) {
        model.addAttribute("appName", "Most Executed Workflows");
        model.addAttribute("apiBaseUrl", "/api/v1");
        model.addAttribute("view", "most-executed");
        return "workflows";
    }

    @GetMapping("/workflows/most-successful")
    public String mostSuccessful(Model model) {
        model.addAttribute("appName", "Most Successful Workflows");
        model.addAttribute("apiBaseUrl", "/api/v1");
        model.addAttribute("view", "most-successful");
        return "workflows";
    }

    @GetMapping("/workflows/recently-created")
    public String recentlyCreated(Model model) {
        model.addAttribute("appName", "Recently Created Workflows");
        model.addAttribute("apiBaseUrl", "/api/v1");
        model.addAttribute("view", "recently-created");
        return "workflows";
    }

    @GetMapping("/workflows/recently-executed")
    public String recentlyExecuted(Model model) {
        model.addAttribute("appName", "Recently Executed Workflows");
        model.addAttribute("apiBaseUrl", "/api/v1");
        model.addAttribute("view", "recently-executed");
        return "workflows";
    }

    @GetMapping("/workflows/never-executed")
    public String neverExecuted(Model model) {
        model.addAttribute("appName", "Never Executed Workflows");
        model.addAttribute("apiBaseUrl", "/api/v1");
        model.addAttribute("view", "never-executed");
        return "workflows";
    }

    @GetMapping("/workflows/statistics")
    public String statistics(Model model) {
        model.addAttribute("appName", "Workflow Statistics");
        model.addAttribute("apiBaseUrl", "/api/v1");
        model.addAttribute("view", "statistics");
        return "workflows";
    }
}
