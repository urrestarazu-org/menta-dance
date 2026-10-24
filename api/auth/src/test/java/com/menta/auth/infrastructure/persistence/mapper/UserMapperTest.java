package com.menta.auth.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserProfile;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.infrastructure.persistence.entity.UserJpaEntity;
import com.menta.shared.domain.vo.Email;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserMapperTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 3, 10, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 8, 3, 11, 0);

    @Test
    void reloads_persisted_token_version_for_stale_refresh_detection() {
        UserJpaEntity persisted = new UserJpaEntity(
            USER_ID,
            "student@example.com",
            "password-hash",
            Role.STUDENT,
            UserStatus.ACTIVE,
            CREATED_AT,
            UPDATED_AT,
            2L,
            null,
            null,
            null,
            null
        );

        User reloaded = UserMapper.toDomain(persisted);

        assertThat(reloaded.getTokenVersion()).isEqualTo(2L);
        assertThat(reloaded.getTokenVersion()).isNotEqualTo(1L);
    }

    @Test
    void preserves_bumped_token_version_when_mapping_back_to_persistence() {
        User compromisedUser = User.rehydrate(
            UserId.of(USER_ID),
            Email.of("student@example.com"),
            "password-hash",
            Role.STUDENT,
            UserStatus.ACTIVE,
            CREATED_AT,
            UPDATED_AT,
            2L
        );

        UserJpaEntity persisted = UserMapper.toJpaEntity(compromisedUser);

        assertThat(persisted.getTokenVersion()).isEqualTo(2L);
    }

    @Test
    void maps_the_profile_to_its_columns() {
        UserProfile profile = new UserProfile("Ada", "Lovelace", "+5491100000000", LocalDate.of(1990, 5, 17));
        User user = User.rehydrate(
            UserId.of(USER_ID), Email.of("student@example.com"), "password-hash", Role.STUDENT,
            UserStatus.PENDING_ACTIVATION, CREATED_AT, UPDATED_AT, 1L, profile
        );

        UserJpaEntity persisted = UserMapper.toJpaEntity(user);

        assertThat(persisted.getFirstName()).isEqualTo("Ada");
        assertThat(persisted.getLastName()).isEqualTo("Lovelace");
        assertThat(persisted.getPhone()).isEqualTo("+5491100000000");
        assertThat(persisted.getBirthDate()).isEqualTo(LocalDate.of(1990, 5, 17));
    }

    @Test
    void maps_a_missing_birth_date_to_a_null_column_and_back() {
        UserProfile profile = new UserProfile("Ada", "Lovelace", "+5491100000000", null);
        User user = User.rehydrate(
            UserId.of(USER_ID), Email.of("student@example.com"), "password-hash", Role.STUDENT,
            UserStatus.ACTIVE, CREATED_AT, UPDATED_AT, 1L, profile
        );

        UserJpaEntity persisted = UserMapper.toJpaEntity(user);
        User reloaded = UserMapper.toDomain(persisted);

        assertThat(persisted.getBirthDate()).isNull();
        assertThat(reloaded.getProfile()).contains(profile);
    }

    @Test
    void leaves_every_profile_column_null_for_a_user_without_a_profile() {
        User user = User.rehydrate(
            UserId.of(USER_ID), Email.of("legacy@example.com"), "password-hash", Role.ADMIN,
            UserStatus.ACTIVE, CREATED_AT, UPDATED_AT, 1L
        );

        UserJpaEntity persisted = UserMapper.toJpaEntity(user);

        assertThat(persisted.getFirstName()).isNull();
        assertThat(persisted.getLastName()).isNull();
        assertThat(persisted.getPhone()).isNull();
        assertThat(persisted.getBirthDate()).isNull();
    }

    @Test
    void rehydrates_a_legacy_row_without_profile_as_a_user_without_profile() {
        UserJpaEntity legacy = new UserJpaEntity(
            USER_ID, "legacy@example.com", "password-hash", Role.ADMIN, UserStatus.ACTIVE,
            CREATED_AT, UPDATED_AT, 1L, null, null, null, null
        );

        User reloaded = UserMapper.toDomain(legacy);

        assertThat(reloaded.getProfile()).isEmpty();
        assertThat(reloaded.getEmail()).isEqualTo(Email.of("legacy@example.com"));
    }

    @Test
    void rehydrates_a_row_with_profile_columns_as_a_user_with_that_profile() {
        UserJpaEntity row = new UserJpaEntity(
            USER_ID, "student@example.com", "password-hash", Role.STUDENT, UserStatus.ACTIVE,
            CREATED_AT, UPDATED_AT, 1L, "Ada", "Lovelace", "+5491100000000", LocalDate.of(1990, 5, 17)
        );

        User reloaded = UserMapper.toDomain(row);

        assertThat(reloaded.getProfile())
            .contains(new UserProfile("Ada", "Lovelace", "+5491100000000", LocalDate.of(1990, 5, 17)));
    }
}
