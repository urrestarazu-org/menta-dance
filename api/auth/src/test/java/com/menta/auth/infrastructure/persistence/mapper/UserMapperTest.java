package com.menta.auth.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.infrastructure.persistence.entity.UserJpaEntity;
import com.menta.shared.domain.vo.Email;
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
            2L
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
}
