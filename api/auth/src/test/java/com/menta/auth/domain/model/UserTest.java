package com.menta.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.shared.domain.vo.Email;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
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

    @Test
    void create_returns_an_already_active_user() {
        User user = User.create(Email.of("student@example.com"), "hash", Role.STUDENT);

        assertThat(user.isActive()).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getId()).isNotNull();
    }

    @Test
    void deactivate_moves_an_active_user_to_inactive() {
        User user = activeUser();

        user.deactivate();

        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    void equal_when_ids_match() {
        UserId id = UserId.generate();
        LocalDateTime now = LocalDateTime.now();
        User a = new User(id, Email.of("a@example.com"), "hash-a", Role.STUDENT, UserStatus.ACTIVE, now, now);
        User b = new User(id, Email.of("b@example.com"), "hash-b", Role.ADMIN, UserStatus.INACTIVE, now, now);

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    void not_equal_to_null_a_different_type_or_a_different_id() {
        User user = activeUser();

        assertThat(user).isEqualTo(user);
        assertThat(user).isNotEqualTo(null);
        assertThat(user).isNotEqualTo("not-a-user");
        assertThat(user).isNotEqualTo(activeUser());
    }

    @Test
    void to_string_reports_identity_without_the_password_hash() {
        User user = activeUser();

        assertThat(user.toString())
            .contains("id=" + user.getId())
            .contains("email=" + user.getEmail())
            .contains("role=" + user.getRole())
            .contains("status=" + user.getStatus())
            .doesNotContain("old-hash");
    }

    @Test
    void register_keeps_the_profile_and_waits_for_activation() {
        UserProfile profile = new UserProfile("Ada", "Lovelace", "+5491100000000", LocalDate.of(1990, 5, 17));

        User user = User.register(Email.of("student@example.com"), "hash", Role.STUDENT, profile);

        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(user.getProfile()).contains(profile);
    }

    @Test
    void register_requires_a_profile() {
        Email email = Email.of("student@example.com");

        assertThatThrownBy(() -> User.register(email, "hash", Role.STUDENT, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void users_created_without_a_profile_report_it_as_absent() {
        assertThat(activeUser().getProfile()).isEqualTo(Optional.empty());
        assertThat(User.create(Email.of("student@example.com"), "hash", Role.STUDENT).getProfile()).isEmpty();
    }

    @Test
    void rehydrate_with_a_profile_restores_it_and_without_one_leaves_it_absent() {
        UserProfile profile = new UserProfile("Ada", "Lovelace", "+5491100000000", null);
        LocalDateTime now = LocalDateTime.now();
        UserId id = UserId.generate();
        Email email = Email.of("student@example.com");

        User withProfile = User.rehydrate(id, email, "hash", Role.STUDENT, UserStatus.ACTIVE, now, now, 3L, profile);
        User withoutProfile = User.rehydrate(id, email, "hash", Role.STUDENT, UserStatus.ACTIVE, now, now, 3L);

        assertThat(withProfile.getProfile()).contains(profile);
        assertThat(withProfile.getTokenVersion()).isEqualTo(3L);
        assertThat(withoutProfile.getProfile()).isEmpty();
    }

    @Test
    void to_string_never_includes_the_profile() {
        UserProfile profile = new UserProfile("Ada", "Lovelace", "+5491100000000", null);
        User user = User.register(Email.of("student@example.com"), "hash", Role.STUDENT, profile);

        assertThat(user.toString()).doesNotContain("Ada").doesNotContain("Lovelace").doesNotContain("+5491100000000");
    }
}
