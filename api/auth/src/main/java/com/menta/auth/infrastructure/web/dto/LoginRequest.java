package com.menta.auth.infrastructure.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Login endpoint request body.
 *
 * Maps to {@link com.menta.auth.application.dto.LoginCommand} at the
 * controller boundary. Validation lives in the web layer per Clean
 * Architecture — the application layer accepts deliberate inputs.
 */
public record LoginRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Password is required")
    String password
) {
}
