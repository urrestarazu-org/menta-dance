package com.menta.physical.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.physical.domain.model.PhysicalSession;
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
    void maps_every_field_to_domain_and_computes_available_spots() {
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

        PhysicalSession session = PhysicalSessionJpaMapper.toDomain(projection);

        assertThat(session.getId().getValue()).isEqualTo(id);
        assertThat(session.getCourseId().getValue()).isEqualTo(courseId);
        assertThat(session.getScheduledAt()).isEqualTo(scheduledAt);
        assertThat(session.getCapacity()).isEqualTo(20);
        assertThat(session.getAssignedSpots()).isEqualTo(5);
        assertThat(session.getActiveCapacityHolds()).isEqualTo(3);
        assertThat(session.getAvailableSpots()).isEqualTo(12);
    }
}
