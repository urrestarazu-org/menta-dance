package com.menta.auth.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserAuditJpaEntityTest {

    @Test
    void exposes_what_it_was_built_with() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-11-10T12:00:00Z");

        UserAuditJpaEntity entity =
            new UserAuditJpaEntity(id, userId, actorId, "ROLE_CHANGED", "STUDENT", "INSTRUCTOR", createdAt);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getActorId()).isEqualTo(actorId);
        assertThat(entity.getAction()).isEqualTo("ROLE_CHANGED");
        assertThat(entity.getPreviousValue()).isEqualTo("STUDENT");
        assertThat(entity.getNewValue()).isEqualTo("INSTRUCTOR");
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void maps_the_auth_user_audit_table() {
        assertThat(UserAuditJpaEntity.class.getAnnotation(Table.class).name()).isEqualTo("auth_user_audit");
    }

    @Test
    void every_column_is_append_only() throws NoSuchFieldException {
        for (String name : new String[] {
            "id", "userId", "actorId", "action", "previousValue", "newValue", "createdAt"
        }) {
            Field field = UserAuditJpaEntity.class.getDeclaredField(name);

            assertThat(field.getAnnotation(Column.class).updatable())
                .as("column of %s must not be updatable", name).isFalse();
        }
    }
}
