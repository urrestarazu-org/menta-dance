package com.menta.physical.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionAvailabilityProjection;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * RED-GREEN: validates the JPA adapter that owns the
 * {@code physical_capacity_assignments} write path (TASK-005).
 */
class JpaPhysicalCapacityAssignmentAdapterTest {

    private static final UUID SESSION_UUID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID STUDENT_UUID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final SessionId SESSION_ID = SessionId.of(SESSION_UUID);
    private static final Instant NOW = Instant.parse("2026-08-24T13:00:00Z");

    private PhysicalCapacityAssignmentJpaRepository assignmentRepository;
    private PhysicalSessionJpaRepository sessionRepository;
    private JpaPhysicalCapacityAssignmentAdapter adapter;

    @BeforeEach
    void setUp() {
        assignmentRepository = mock(PhysicalCapacityAssignmentJpaRepository.class);
        sessionRepository = mock(PhysicalSessionJpaRepository.class);
        when(assignmentRepository.save(any(PhysicalCapacityAssignmentJpaEntity.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        com.menta.physical.application.port.out.Clock clock = () -> NOW;
        adapter = new JpaPhysicalCapacityAssignmentAdapter(
            assignmentRepository, sessionRepository, clock
        );
    }

    private void stubSessionWithCapacity(int capacity, int assignedSpots) {
        when(sessionRepository.findByIdWithAvailabilityForUpdate(SESSION_UUID, NOW))
            .thenReturn(Optional.of(projection(capacity, assignedSpots)));
    }

    private static PhysicalSessionAvailabilityProjection projection(int capacity, int assigned) {
        return new PhysicalSessionAvailabilityProjection() {
            @Override public byte[] getId() { return null; }
            @Override public byte[] getCourseId() { return null; }
            @Override public Instant getScheduledAt() { return null; }
            @Override public Integer getCapacity() { return capacity; }
            @Override public Integer getAssignedSpots() { return assigned; }
            @Override public Integer getActiveCapacityHolds() { return 0; }
            @Override public String getStatus() { return "SCHEDULED"; }
            @Override public String getNotes() { return null; }
        };
    }

    @Nested
    @DisplayName("Write method persists a row matching the V7 schema columns")
    class WriteShape {

        @Test
        void assertAssignment_saves_one_row_when_assignedSpots_plus_one_does_not_exceed_capacity() {
            stubSessionWithCapacity(2, 0);

            Instant returned = adapter.assertAssignment(SESSION_UUID, STUDENT_UUID);

            assertThat(returned).isEqualTo(NOW);

            ArgumentCaptor<PhysicalCapacityAssignmentJpaEntity> captor = ArgumentCaptor.forClass(
                PhysicalCapacityAssignmentJpaEntity.class
            );
            verify(assignmentRepository).save(captor.capture());

            PhysicalCapacityAssignmentJpaEntity row = captor.getValue();
            assertThat(row.getId()).isNotNull();
            assertThat(row.getSessionId()).isEqualTo(SESSION_UUID);
            assertThat(row.getStudentId()).isEqualTo(STUDENT_UUID);
            assertThat(row.getCreatedAt()).isEqualTo(NOW);
        }

        @Test
        void assertAssignment_deletes_and_throws_when_assignedSpots_plus_one_exceeds_capacity() {
            stubSessionWithCapacity(1, 1);

            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                adapter.assertAssignment(SESSION_UUID, STUDENT_UUID)
            ).isInstanceOf(com.menta.physical.domain.exception.CapacityBelowAssignedException.class);

            verify(assignmentRepository).deleteById(any(UUID.class));
        }
    }
}
