package com.menta.auth.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserJpaEntityTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 16, 12, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 8, 16, 13, 0);
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 5, 17);

    @Test
    void exposes_every_field_supplied_to_the_full_constructor() {
        UserJpaEntity entity = new UserJpaEntity(
            ID, "student@example.com", "hashed-password", Role.STUDENT, UserStatus.ACTIVE,
            CREATED_AT, UPDATED_AT, 1L, "Ada", "Lovelace", "+5491100000000", BIRTH_DATE
        );

        assertThat(entity.getId()).isEqualTo(ID);
        assertThat(entity.getEmail()).isEqualTo("student@example.com");
        assertThat(entity.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(entity.getRole()).isEqualTo(Role.STUDENT);
        assertThat(entity.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(entity.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(entity.getUpdatedAt()).isEqualTo(UPDATED_AT);
        assertThat(entity.getTokenVersion()).isEqualTo(1L);
        assertThat(entity.getFirstName()).isEqualTo("Ada");
        assertThat(entity.getLastName()).isEqualTo("Lovelace");
        assertThat(entity.getPhone()).isEqualTo("+5491100000000");
        assertThat(entity.getBirthDate()).isEqualTo(BIRTH_DATE);
    }

    @Test
    void every_setter_mutates_its_matching_field() {
        UserJpaEntity entity = new UserJpaEntity(
            ID, "student@example.com", "hashed-password", Role.STUDENT, UserStatus.ACTIVE,
            CREATED_AT, UPDATED_AT, 1L, "Ada", "Lovelace", "+5491100000000", BIRTH_DATE
        );
        UUID newId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        entity.setId(newId);
        entity.setEmail("other@example.com");
        entity.setPasswordHash("new-hash");
        entity.setRole(Role.ADMIN);
        entity.setStatus(UserStatus.LOCKED);
        entity.setCreatedAt(CREATED_AT.plusDays(1));
        entity.setUpdatedAt(UPDATED_AT.plusDays(1));
        entity.setTokenVersion(2L);
        entity.setFirstName("Grace");
        entity.setLastName("Hopper");
        entity.setPhone("+12025550100");
        entity.setBirthDate(BIRTH_DATE.plusDays(1));

        assertThat(entity.getId()).isEqualTo(newId);
        assertThat(entity.getEmail()).isEqualTo("other@example.com");
        assertThat(entity.getPasswordHash()).isEqualTo("new-hash");
        assertThat(entity.getRole()).isEqualTo(Role.ADMIN);
        assertThat(entity.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(entity.getCreatedAt()).isEqualTo(CREATED_AT.plusDays(1));
        assertThat(entity.getUpdatedAt()).isEqualTo(UPDATED_AT.plusDays(1));
        assertThat(entity.getTokenVersion()).isEqualTo(2L);
        assertThat(entity.getFirstName()).isEqualTo("Grace");
        assertThat(entity.getLastName()).isEqualTo("Hopper");
        assertThat(entity.getPhone()).isEqualTo("+12025550100");
        assertThat(entity.getBirthDate()).isEqualTo(BIRTH_DATE.plusDays(1));
    }
}
