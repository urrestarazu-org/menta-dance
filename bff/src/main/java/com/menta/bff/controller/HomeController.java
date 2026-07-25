package com.menta.bff.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Home controller for the BFF.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("appName", "Menta Dance");
        return "index";
    }

    @GetMapping("/health")
    public String health() {
        return "health";
    }
}
