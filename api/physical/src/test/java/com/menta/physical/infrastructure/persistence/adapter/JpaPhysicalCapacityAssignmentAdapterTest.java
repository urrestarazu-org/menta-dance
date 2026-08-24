package com.menta.physical.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.domain.model.SessionId;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * RED-GREEN: validates the JPA adapter that owns the
 * {@code physical_capacity_assignments} write path (TASK-005). Asserts
 * the read-method delegation AND the persistence shape of
 * {@link com.menta.physical.application.port.out.PhysicalCapacityAssignmentWriter#assertAssignment}.
 */
class JpaPhysicalCapacityAssignmentAdapterTest {

    private static final UUID SESSION_UUID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID STUDENT_UUID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final SessionId SESSION_ID = SessionId.of(SESSION_UUID);
    private static final Instant NOW = Instant.parse("2026-08-24T13:00:00Z");

    private PhysicalCapacityAssignmentJpaRepository jpaRepository;
    private JpaPhysicalCapacityAssignmentAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(PhysicalCapacityAssignmentJpaRepository.class);
        when(jpaRepository.save(any(PhysicalCapacityAssignmentJpaEntity.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        adapter = new JpaPhysicalCapacityAssignmentAdapter(
            jpaRepository, Clock.fixed(NOW, ZoneId.of("UTC"))
        );
    }

    @Nested
    @DisplayName("Read method preserved on the existing port")
    class ReadDelegation {

        @Test
        void existsConfirmedAssignment_delegates_to_repository_with_sessionId_and_studentId() {
            when(jpaRepository.existsBySessionIdAndStudentId(SESSION_UUID, STUDENT_UUID)).thenReturn(true);

            boolean result = adapter.existsConfirmedAssignment(SESSION_ID, STUDENT_UUID);

            assertThat(result).isTrue();
            verify(jpaRepository).existsBySessionIdAndStudentId(SESSION_UUID, STUDENT_UUID);
        }
    }

    @Nested
    @DisplayName("Write method persists a row matching the V7 schema columns")
    class WriteShape {

        @Test
        void assertAssignment_saves_one_row_with_fresh_row_id_sessionId_studentId_now() {
            Instant returned = adapter.assertAssignment(SESSION_UUID, STUDENT_UUID);

            assertThat(returned).isEqualTo(NOW);

            ArgumentCaptor<PhysicalCapacityAssignmentJpaEntity> captor = ArgumentCaptor.forClass(
                PhysicalCapacityAssignmentJpaEntity.class
            );
            verify(jpaRepository).save(captor.capture());

            PhysicalCapacityAssignmentJpaEntity row = captor.getValue();
            assertThat(row.getId()).isNotNull();
            assertThat(row.getSessionId()).isEqualTo(SESSION_UUID);
            assertThat(row.getStudentId()).isEqualTo(STUDENT_UUID);
            assertThat(row.getCreatedAt()).isEqualTo(NOW);
        }
    }
}
