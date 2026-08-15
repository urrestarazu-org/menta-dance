package com.menta.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.shared.domain.vo.Email;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Email EMAIL = Email.of("student@example.com");

    @Test
    void public_registration_starts_pending_activation() {
        User user = User.register(EMAIL, "password-hash", Role.STUDENT);

        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    void pending_user_can_be_activated_once() {
        User user = User.register(EMAIL, "password-hash", Role.STUDENT);

        user.activate();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThatThrownBy(user::activate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("pending activation");
    }

    @Test
    void active_provisioned_user_cannot_run_activation_transition() {
        User active = User.create(EMAIL, "password-hash", Role.ADMIN);

        assertThatThrownBy(active::activate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("pending activation");
    }
}
