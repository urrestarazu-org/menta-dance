package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.usecase.LogoutUseCase;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Logout controller - handles user logout.
 * <p>
 * Delegates logout logic to {@link LogoutUseCase}.
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class LogoutController {

    private final LogoutUseCase logoutUseCase;

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        try {
            logoutUseCase.execute();
            log.info("User logged out successfully");

            // Invalidate session after logout use case clears tokens
            if (session != null) {
                session.invalidate();
            }

            return "redirect:/login?logout";

        } catch (Exception e) {
            log.error("Error during logout: {}", e.getMessage(), e);
            // Even if logout fails, redirect to login (fail-open for logout)
            return "redirect:/login?logout";
        }
    }
}
