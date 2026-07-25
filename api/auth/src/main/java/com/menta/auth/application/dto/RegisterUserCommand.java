package com.menta.auth.application.dto;

import com.menta.auth.domain.model.Role;

/**
 * Command to register a new user.
 * Immutable DTO for application layer.
 */
public record RegisterUserCommand(
    String email,
    String password,
    Role role
) {
}
