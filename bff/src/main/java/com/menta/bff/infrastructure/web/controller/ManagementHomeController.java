package com.menta.bff.infrastructure.web.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Home of the management area (#354).
 *
 * <p>Access is restricted to the staff roles in {@code BffSecurityConfig}; the section links come from
 * the shared management layout, so this controller only greets the user.
 * </p>
 */
@Controller
public class ManagementHomeController {

    @GetMapping("/gestion")
    public String home(Authentication authentication, Model model) {
        if (authentication != null) {
            model.addAttribute("username", authentication.getName());
        }
        return "gestion/home";
    }
}
