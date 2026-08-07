package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.LoginCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.usecase.LoginUseCase;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Login controller - handles login page display and authentication.
 * <p>
 * Delegates actual authentication logic to {@link LoginUseCase}.
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class LoginController {

    private final LoginUseCase loginUseCase;

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            Model model
    ) {
        if (error != null) {
            model.addAttribute("error", true);
            model.addAttribute("errorMessage", "Invalid credentials. Please try again.");
        }

        if (logout != null) {
            model.addAttribute("logout", true);
            model.addAttribute("logoutMessage", "You have been logged out successfully.");
        }

        return "login";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            HttpSession session,
            Model model
    ) {
        try {
            LoginCommand command = new LoginCommand(email, password);
            loginUseCase.execute(command);

            log.info("User logged in successfully: {}", email);
            return "redirect:/dashboard";

        } catch (AuthApiClient.AuthenticationException e) {
            log.warn("Authentication failed for user: {}", email);
            model.addAttribute("error", true);
            model.addAttribute("errorMessage", "Invalid credentials. Please try again.");
            return "login";

        } catch (AuthApiClient.ServiceUnavailableException e) {
            log.error("Auth API unavailable during login: {}", e.getMessage());
            model.addAttribute("error", true);
            model.addAttribute("errorMessage", "Service temporarily unavailable. Please try again later.");
            return "login";

        } catch (Exception e) {
            log.error("Unexpected error during login: {}", e.getMessage(), e);
            model.addAttribute("error", true);
            model.addAttribute("errorMessage", "An unexpected error occurred. Please try again.");
            return "login";
        }
    }
}
