package com.menta.physical.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionAvailabilityProjection;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalSessionJpaMapperTest {

    private static byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    @Test
    void maps_projection_to_domain_and_computes_available_spots() {
        UUID id = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-08-25T22:00:00Z");
        PhysicalSessionAvailabilityProjection projection = mock(PhysicalSessionAvailabilityProjection.class);
        when(projection.getId()).thenReturn(toBytes(id));
        when(projection.getCourseId()).thenReturn(toBytes(courseId));
        when(projection.getScheduledAt()).thenReturn(scheduledAt);
        when(projection.getCapacity()).thenReturn(20);
        when(projection.getAssignedSpots()).thenReturn(5);
        when(projection.getActiveCapacityHolds()).thenReturn(3);
        when(projection.getStatus()).thenReturn("SCHEDULED");
        when(projection.getNotes()).thenReturn("Clase especial");

        PhysicalSession session = PhysicalSessionJpaMapper.toDomain(projection);

        assertThat(session.getId().getValue()).isEqualTo(id);
        assertThat(session.getCourseId().getValue()).isEqualTo(courseId);
        assertThat(session.getScheduledAt()).isEqualTo(scheduledAt);
        assertThat(session.getCapacity()).isEqualTo(20);
        assertThat(session.getAssignedSpots()).isEqualTo(5);
        assertThat(session.getActiveCapacityHolds()).isEqualTo(3);
        assertThat(session.getAvailableSpots()).isEqualTo(12);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(session.getNotes()).isEqualTo("Clase especial");
    }

    @Test
    void maps_a_freshly_created_session_entity_with_zero_availability() {
        UUID id = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-08-25T22:00:00Z");
        PhysicalSessionJpaEntity entity = new PhysicalSessionJpaEntity(
            id, courseId, scheduledAt, 20, "SCHEDULED", null
        );

        PhysicalSession session = PhysicalSessionJpaMapper.toDomainFreshlyCreated(entity);

        assertThat(session.getId().getValue()).isEqualTo(id);
        assertThat(session.getCourseId().getValue()).isEqualTo(courseId);
        assertThat(session.getAssignedSpots()).isZero();
        assertThat(session.getActiveCapacityHolds()).isZero();
        assertThat(session.getAvailableSpots()).isEqualTo(20);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
    }

    @Test
    void round_trips_domain_to_entity() {
        PhysicalSession session = PhysicalSession.create(
            com.menta.physical.domain.model.CourseId.generate(),
            Instant.parse("2026-08-25T22:00:00Z"), 20, "Nota"
        );

        PhysicalSessionJpaEntity entity = PhysicalSessionJpaMapper.toEntity(session);

        assertThat(entity.getId()).isEqualTo(session.getId().getValue());
        assertThat(entity.getCourseId()).isEqualTo(session.getCourseId().getValue());
        assertThat(entity.getScheduledAt()).isEqualTo(session.getScheduledAt());
        assertThat(entity.getCapacity()).isEqualTo(20);
        assertThat(entity.getStatus()).isEqualTo("SCHEDULED");
        assertThat(entity.getNotes()).isEqualTo("Nota");
    }
}
