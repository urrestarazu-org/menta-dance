package com.menta.bff.infrastructure.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Login view controller.
 *
 * Only handles GET /login to display the login form.
 * POST /login is handled automatically by Spring Security
 * via BffAuthenticationProvider.
 */
@Controller
public class LoginViewController {

    @GetMapping("/login")
    public String loginForm(
        @RequestParam(value = "error", required = false) String error,
        @RequestParam(value = "logout", required = false) String logout,
        Model model
    ) {
        if (error != null) {
            model.addAttribute("error", "Email o contraseña inválidos");
        }
        if (logout != null) {
            model.addAttribute("logout", "Sesión cerrada exitosamente");
        }
        return "login";
    }
}
