package com.menta.bff.infrastructure.web.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Dashboard controller - authenticated endpoint for testing.
 *
 * <p>Requires authentication to access. The greeting uses the {@link Authentication} name: the
 * BFF authentication provider carries the user's email as principal (a {@code String}), not a
 * {@code UserDetails}, so the name is read from the authentication itself (#492).
 * </p>
 */
@Controller
public class DashboardController {

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        if (authentication != null) {
            model.addAttribute("username", authentication.getName());
        }
        return "dashboard";
    }
}
