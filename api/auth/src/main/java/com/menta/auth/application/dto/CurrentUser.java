package com.menta.auth.application.dto;

import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserProfile;
import java.util.UUID;

/**
 * What an authenticated user sees of their own account: identity, names and role. The email, the credentials,
 * the status and the rest of the profile (phone, birth date) are deliberately not part of it.
 *
 * @param firstName {@code null} for accounts without profile (demo, admin, legacy rows)
 * @param lastName {@code null} for accounts without profile
 */
public record CurrentUser(UUID userId, String firstName, String lastName, Role role) {

    /** Projects the user, taking the names from its profile when it has one. */
    public static CurrentUser from(User user) {
        UserProfile profile = user.getProfile().orElse(null);
        return new CurrentUser(
            user.getId().getValue(),
            profile == null ? null : profile.firstName(),
            profile == null ? null : profile.lastName(),
            user.getRole()
        );
    }
}
