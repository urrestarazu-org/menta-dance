package com.menta.physical.infrastructure.persistence.mapper;

import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.domain.model.SessionStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionAvailabilityProjection;
import java.nio.ByteBuffer;
import java.util.UUID;

/** Manual mapper JPA ↔ domain — no MapStruct (unused in this project, see #96). */
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
            projection.getActiveCapacityHolds(),
            SessionStatus.valueOf(projection.getStatus()),
            projection.getNotes()
        );
    }

    /**
     * Used ONLY for a brand-new session created via {@link
     * PhysicalSession#create} (batch creation): {@code assignedSpots}/
     * {@code activeCapacityHolds} are correctly {@code 0} because nothing
     * could reference a {@link com.menta.physical.domain.model.SessionId}
     * that did not exist a moment ago. Never use this for a session that
     * may already carry assignments — read it back through the
     * availability-by-id query instead (see {@code
     * PhysicalSessionRepositoryAdapter}).
     */
    public static PhysicalSession toDomainFreshlyCreated(PhysicalSessionJpaEntity entity) {
        return new PhysicalSession(
            SessionId.of(entity.getId()),
            CourseId.of(entity.getCourseId()),
            entity.getScheduledAt(),
            entity.getCapacity(),
            0,
            0,
            SessionStatus.valueOf(entity.getStatus()),
            entity.getNotes()
        );
    }

    public static PhysicalSessionJpaEntity toEntity(PhysicalSession session) {
        return new PhysicalSessionJpaEntity(
            session.getId().getValue(),
            session.getCourseId().getValue(),
            session.getScheduledAt(),
            session.getCapacity(),
            session.getStatus().name(),
            session.getNotes()
        );
    }

    /** Decodes a BINARY(16) column read from a native query — same 16-byte layout Hibernate writes. */
    private static UUID toUuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
