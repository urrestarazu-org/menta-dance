package com.menta.auth.infrastructure.web.dto;

import com.menta.auth.domain.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for user registration.
 * Infrastructure layer - web request object.
 */
public record RegisterUserRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Password is required")
    String password,

    @NotNull(message = "Role is required")
    Role role
) {
}
