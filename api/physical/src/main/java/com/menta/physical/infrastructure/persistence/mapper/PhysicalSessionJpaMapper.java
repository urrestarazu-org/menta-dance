package com.menta.physical.infrastructure.persistence.mapper;

import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionAvailabilityProjection;
import java.nio.ByteBuffer;
import java.util.UUID;

/** Manual mapper JPA projection ↔ domain — no MapStruct (unused in this project, see #96). */
public final class PhysicalSessionJpaMapper {

    private PhysicalSessionJpaMapper() {
    }

    public static PhysicalSession toDomain(PhysicalSessionAvailabilityProjection projection) {
        return new PhysicalSession(
            SessionId.of(toUuid(projection.getId())),
            CourseId.of(toUuid(projection.getCourseId())),
            projection.getScheduledAt(),
            projection.getCapacity(),
            projection.getAssignedSpots(),
            projection.getActiveCapacityHolds()
        );
    }

    /** Decodes a BINARY(16) column read from a native query — same 16-byte layout Hibernate writes. */
    private static UUID toUuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
