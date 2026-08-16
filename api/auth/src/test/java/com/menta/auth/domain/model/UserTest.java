package com.menta.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.shared.domain.vo.Email;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Covers only {@link User#resetPassword(String)} — every other User behaviour
 * already has coverage elsewhere in this suite.
 */
class UserTest {

    private static User activeUser() {
        LocalDateTime now = LocalDateTime.now();
        return new User(
            UserId.generate(), Email.of("student@example.com"), "old-hash",
            Role.STUDENT, UserStatus.ACTIVE, now, now
        );
    }

    @Test
    void replaces_the_password_hash() {
        User user = activeUser();

        user.resetPassword("new-hash");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    }

    @Test
    void bumps_token_version_in_the_same_call() {
        // The two effects are one business event (US-AUTH-006): a caller must
        // not be able to change the password without also invalidating
        // existing sessions.
        User user = activeUser();
        long before = user.getTokenVersion();

        user.resetPassword("new-hash");

        assertThat(user.getTokenVersion()).isEqualTo(before + 1);
    }

    @Test
    void rejects_a_null_hash() {
        assertThatThrownBy(() -> activeUser().resetPassword(null))
            .isInstanceOf(NullPointerException.class);
    }
}
