package com.menta.auth.application.dto;

import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserProfile;
import com.menta.auth.domain.model.UserStatus;
import java.time.LocalDateTime;

/**
 * What an admin sees of a user: identity, role and state. Credentials and the token version are deliberately
 * not part of it.
 *
 * @param firstName {@code null} for accounts without profile (demo, admin, legacy rows)
 * @param lastName {@code null} for accounts without profile
 */
public record UserSummary(
    String id,
    String email,
    String firstName,
    String lastName,
    Role role,
    UserStatus status,
    boolean demo,
    LocalDateTime createdAt
) {

    /** Projects the user, taking the names from its profile when it has one. */
    public static UserSummary from(User user) {
        UserProfile profile = user.getProfile().orElse(null);
        return new UserSummary(
            user.getId().getValue().toString(),
            user.getEmail().getValue(),
            profile == null ? null : profile.firstName(),
            profile == null ? null : profile.lastName(),
            user.getRole(),
            user.getStatus(),
            user.isDemo(),
            user.getCreatedAt()
        );
    }
}
