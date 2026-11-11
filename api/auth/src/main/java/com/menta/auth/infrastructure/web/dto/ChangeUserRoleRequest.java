package com.menta.auth.infrastructure.web.dto;

import com.menta.auth.domain.model.Role;
import jakarta.validation.constraints.NotNull;

/** Body of {@code PATCH /api/v1/admin/users/{userId}/role}. */
public record ChangeUserRoleRequest(@NotNull Role role) {
}
