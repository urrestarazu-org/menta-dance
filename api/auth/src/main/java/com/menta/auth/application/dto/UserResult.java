package com.menta.auth.application.dto;

import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserStatus;
import java.time.LocalDateTime;

/**
 * Result DTO representing a user.
 * Immutable record for application layer output.
 */
public record UserResult(
    String id,
    String email,
    Role role,
    UserStatus status,
    LocalDateTime createdAt
) {
}
