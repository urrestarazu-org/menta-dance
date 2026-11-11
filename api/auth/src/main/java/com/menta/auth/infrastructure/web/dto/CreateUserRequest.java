package com.menta.auth.infrastructure.web.dto;

import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserProfile;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/admin/users}. There is deliberately no password: the holder chooses it when
 * activating the account.
 */
public record CreateUserRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(max = UserProfile.NAME_MAX_LENGTH) String firstName,
    @NotBlank @Size(max = UserProfile.NAME_MAX_LENGTH) String lastName,
    @NotNull Role role
) {
}
