package com.menta.auth.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.menta.auth.domain.model.UserId;
import com.menta.auth.infrastructure.persistence.entity.UserAuditJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.UserAuditJpaRepository;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class UserAuditRepositoryAdapterTest {

    @Mock private UserAuditJpaRepository auditRepository;

    @Test
    void append_persists_one_row_with_the_exact_given_fields_and_the_instant_it_was_given() {
        UserId userId = UserId.generate();
        UUID actorId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-11-10T12:00:00Z");

        new UserAuditRepositoryAdapter(auditRepository)
            .append(userId, actorId, "ROLE_CHANGED", "STUDENT", "INSTRUCTOR", occurredAt);

        ArgumentCaptor<UserAuditJpaEntity> saved = ArgumentCaptor.forClass(UserAuditJpaEntity.class);
        verify(auditRepository).save(saved.capture());
        UserAuditJpaEntity row = saved.getValue();
        assertThat(row.getId()).isNotNull();
        assertThat(row.getUserId()).isEqualTo(userId.getValue());
        assertThat(row.getActorId()).isEqualTo(actorId);
        assertThat(row.getAction()).isEqualTo("ROLE_CHANGED");
        assertThat(row.getPreviousValue()).isEqualTo("STUDENT");
        assertThat(row.getNewValue()).isEqualTo("INSTRUCTOR");
        assertThat(row.getCreatedAt()).isEqualTo(occurredAt);
    }

    @Test
    void append_joins_the_transaction_of_its_caller() throws NoSuchMethodException {
        Method append = UserAuditRepositoryAdapter.class.getMethod(
            "append", UserId.class, UUID.class, String.class, String.class, String.class, Instant.class
        );

        assertThat(append.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.REQUIRED);
    }
}
