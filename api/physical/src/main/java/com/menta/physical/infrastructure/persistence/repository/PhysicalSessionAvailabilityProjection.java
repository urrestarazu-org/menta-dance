package com.menta.physical.infrastructure.persistence.repository;

import java.time.Instant;

/**
 * Session row joined with a live count of occupied spots — computed by the
 * SQL in {@link PhysicalSessionJpaRepository#findScheduledWithAvailability}/
 * {@link PhysicalSessionJpaRepository#findManagedWithAvailability} at query
 * time, never a cached counter.
 *
 * <p>{@code id}/{@code courseId} are exposed as raw {@code byte[]}: unlike a
 * JPQL entity query, a native-SQL projection gets no Hibernate UUID↔BINARY(16)
 * conversion, so {@link com.menta.physical.infrastructure.persistence.mapper.PhysicalSessionJpaMapper}
 * decodes them manually.</p>
 */
public interface PhysicalSessionAvailabilityProjection {

    byte[] getId();

    byte[] getCourseId();

    Instant getScheduledAt();

    Integer getCapacity();

    Integer getAssignedSpots();

    Integer getActiveCapacityHolds();

    String getStatus();

    String getNotes();
}
