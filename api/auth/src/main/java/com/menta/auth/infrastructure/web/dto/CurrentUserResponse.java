package com.menta.auth.infrastructure.web.dto;

import com.menta.auth.application.dto.CurrentUser;
import com.menta.auth.domain.model.Role;
import java.util.UUID;

/**
 * The authenticated user's own account as {@code GET /api/v1/users/me} shows it. Never carries the email, the
 * credentials, the status, the phone or the birth date.
 *
 * @param firstName {@code null} for accounts without profile
 * @param lastName {@code null} for accounts without profile
 */
public record CurrentUserResponse(UUID userId, String firstName, String lastName, Role role) {

    /** Copies the current user as the HTTP representation. */
    public static CurrentUserResponse from(CurrentUser user) {
        return new CurrentUserResponse(user.userId(), user.firstName(), user.lastName(), user.role());
    }
}
