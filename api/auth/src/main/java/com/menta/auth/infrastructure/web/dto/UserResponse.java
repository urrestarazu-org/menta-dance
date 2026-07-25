package com.menta.auth.infrastructure.web.dto;

import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserStatus;

/**
 * Response DTO for user data.
 * Infrastructure layer - web response object.
 */
public record UserResponse(
    String id,
    String email,
    Role role,
    UserStatus status
) {
}
