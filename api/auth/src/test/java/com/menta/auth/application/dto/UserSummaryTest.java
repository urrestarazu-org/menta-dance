package com.menta.auth.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserProfile;
import com.menta.auth.domain.model.UserStatus;
import com.menta.shared.domain.vo.Email;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UserSummaryTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 1, 9, 30);

    @Test
    void maps_a_user_with_profile() {
        UserId id = UserId.generate();
        User user = User.rehydrate(
            id, Email.of("ada@example.com"), "secret-hash", Role.INSTRUCTOR, UserStatus.ACTIVE,
            CREATED_AT, CREATED_AT, 7L, new UserProfile("Ada", "Lovelace", "+5491100000000", null), true
        );

        UserSummary summary = UserSummary.from(user);

        assertThat(summary.id()).isEqualTo(id.getValue().toString());
        assertThat(summary.email()).isEqualTo("ada@example.com");
        assertThat(summary.firstName()).isEqualTo("Ada");
        assertThat(summary.lastName()).isEqualTo("Lovelace");
        assertThat(summary.role()).isEqualTo(Role.INSTRUCTOR);
        assertThat(summary.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(summary.demo()).isTrue();
        assertThat(summary.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void leaves_the_names_null_for_an_account_without_profile() {
        User user = User.rehydrate(
            UserId.generate(), Email.of("admin@example.com"), "hash", Role.ADMIN, UserStatus.ACTIVE,
            CREATED_AT, CREATED_AT, 1L
        );

        UserSummary summary = UserSummary.from(user);

        assertThat(summary.firstName()).isNull();
        assertThat(summary.lastName()).isNull();
        assertThat(summary.demo()).isFalse();
    }

    @Test
    void never_exposes_credentials_or_token_version() {
        assertThat(UserSummary.class.getRecordComponents())
            .extracting(component -> component.getName())
            .doesNotContain("passwordHash", "tokenVersion");
    }
}
