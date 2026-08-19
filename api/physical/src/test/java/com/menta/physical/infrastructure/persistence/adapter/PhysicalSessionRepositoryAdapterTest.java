package com.menta.physical.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionAvailabilityProjection;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalSessionRepositoryAdapterTest {

    private final PhysicalSessionJpaRepository sessionRepository = mock(PhysicalSessionJpaRepository.class);
    private final PhysicalSessionRepositoryAdapter adapter = new PhysicalSessionRepositoryAdapter(sessionRepository);

    private static byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    private static PhysicalSessionAvailabilityProjection projection(
        UUID id, UUID courseId, Instant scheduledAt, int capacity, int assignedSpots, String status
    ) {
        PhysicalSessionAvailabilityProjection projection = mock(PhysicalSessionAvailabilityProjection.class);
        when(projection.getId()).thenReturn(toBytes(id));
        when(projection.getCourseId()).thenReturn(toBytes(courseId));
        when(projection.getScheduledAt()).thenReturn(scheduledAt);
        when(projection.getCapacity()).thenReturn(capacity);
        when(projection.getAssignedSpots()).thenReturn(assignedSpots);
        when(projection.getActiveCapacityHolds()).thenReturn(0);
        when(projection.getStatus()).thenReturn(status);
        when(projection.getNotes()).thenReturn(null);
        return projection;
    }

    @Test
    void find_scheduled_maps_every_projection_to_domain() {
        CourseId courseId = CourseId.generate();
        UUID sessionId = UUID.randomUUID();
        Instant from = Instant.now();
        Instant to = from.plusSeconds(86400);
        PhysicalSessionAvailabilityProjection scheduledProjection =
            projection(sessionId, courseId.getValue(), from, 20, 5, "SCHEDULED");
        when(sessionRepository.findScheduledWithAvailability(any(), any(), any(), any()))
            .thenReturn(List.of(scheduledProjection));

        List<PhysicalSession> result = adapter.findScheduled(courseId, from, to);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId().getValue()).isEqualTo(sessionId);
    }

    @Test
    void find_managed_maps_every_projection_regardless_of_status() {
        CourseId courseId = CourseId.generate();
        UUID sessionId = UUID.randomUUID();
        Instant from = Instant.now();
        Instant to = from.plusSeconds(86400);
        PhysicalSessionAvailabilityProjection cancelledProjection =
            projection(sessionId, courseId.getValue(), from, 20, 5, "CANCELLED");
        when(sessionRepository.findManagedWithAvailability(any(), any(), any(), any()))
            .thenReturn(List.of(cancelledProjection));

        List<PhysicalSession> result = adapter.findManaged(courseId, from, to);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus().name()).isEqualTo("CANCELLED");
    }

    @Test
    void find_by_id_returns_empty_when_absent() {
        SessionId id = SessionId.generate();
        when(sessionRepository.findByIdWithAvailability(any(), any())).thenReturn(Optional.empty());

        assertThat(adapter.findById(id)).isEmpty();
    }

    @Test
    void save_persists_then_re_reads_through_the_availability_query() {
        CourseId courseId = CourseId.generate();
        PhysicalSession session = PhysicalSession.create(courseId, Instant.now().plusSeconds(3600), 20, "Nota");
        when(sessionRepository.save(any(PhysicalSessionJpaEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        PhysicalSessionAvailabilityProjection reReadProjection = projection(
            session.getId().getValue(), courseId.getValue(), session.getScheduledAt(), 20, 3, "SCHEDULED"
        );
        when(sessionRepository.findByIdWithAvailability(any(), any())).thenReturn(Optional.of(reReadProjection));

        PhysicalSession saved = adapter.save(session);

        // Proves the adapter never trusts the in-memory argument's own
        // assignedSpots (0, since it was just created) -- it re-reads the
        // live-availability query instead, which here reports 3.
        assertThat(saved.getAssignedSpots()).isEqualTo(3);
    }

    @Test
    void save_all_returns_freshly_created_sessions_with_zero_availability() {
        CourseId courseId = CourseId.generate();
        PhysicalSession session = PhysicalSession.create(courseId, Instant.now().plusSeconds(3600), 20, null);
        when(sessionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PhysicalSession> saved = adapter.saveAll(List.of(session));

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getAssignedSpots()).isZero();
        assertThat(saved.get(0).getAvailableSpots()).isEqualTo(20);
    }

    @Test
    void has_future_assigned_sessions_delegates_to_the_repository() {
        CourseId courseId = CourseId.generate();
        when(sessionRepository.existsFutureAssignedSession(any(), any())).thenReturn(true);

        assertThat(adapter.hasFutureAssignedSessions(courseId)).isTrue();
    }
}
