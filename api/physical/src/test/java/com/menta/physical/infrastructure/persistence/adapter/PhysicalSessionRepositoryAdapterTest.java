package com.menta.physical.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionAvailabilityProjection;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
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

    @Test
    void returns_an_empty_list_when_there_are_no_sessions() {
        when(sessionRepository.findScheduledWithAvailability(any(), any(), any(), any())).thenReturn(List.of());

        CourseId courseId = CourseId.generate();
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-01T00:00:00Z");

        assertThat(adapter.findScheduled(courseId, from, to)).isEmpty();
    }

    @Test
    void maps_scheduled_sessions_with_live_availability() {
        UUID sessionId = UUID.randomUUID();
        UUID courseUuid = UUID.randomUUID();
        Instant scheduledAt = Instant.parse("2026-08-25T22:00:00Z");
        PhysicalSessionAvailabilityProjection projection = mock(PhysicalSessionAvailabilityProjection.class);
        when(projection.getId()).thenReturn(toBytes(sessionId));
        when(projection.getCourseId()).thenReturn(toBytes(courseUuid));
        when(projection.getScheduledAt()).thenReturn(scheduledAt);
        when(projection.getCapacity()).thenReturn(20);
        when(projection.getAssignedSpots()).thenReturn(5);
        when(projection.getActiveCapacityHolds()).thenReturn(3);
        when(sessionRepository.findScheduledWithAvailability(any(), any(), any(), any()))
            .thenReturn(List.of(projection));

        CourseId courseId = CourseId.of(courseUuid);
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-01T00:00:00Z");

        List<PhysicalSession> result = adapter.findScheduled(courseId, from, to);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId().getValue()).isEqualTo(sessionId);
        assertThat(result.get(0).getAvailableSpots()).isEqualTo(12);
    }
}
