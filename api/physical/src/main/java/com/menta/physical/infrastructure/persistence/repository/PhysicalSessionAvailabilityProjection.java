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

    /** Session id as the raw 16-byte {@code BINARY(16)} value; decoded by the mapper. */
    byte[] getId();

    /** Course id as the raw 16-byte {@code BINARY(16)} value; decoded by the mapper. */
    byte[] getCourseId();

    /** Instant at which the session starts. */
    Instant getScheduledAt();

    /** Configured capacity of the session. */
    Integer getCapacity();

    /** Live count of confirmed assignments, computed by a correlated subquery at query time. */
    Integer getAssignedSpots();

    /** Live count of unconverted holds that have not expired at the query's {@code now}. */
    Integer getActiveCapacityHolds();

    /** Session status name, parsed into {@code SessionStatus} by the mapper. */
    String getStatus();

    /** Free-text notes of the session; may be {@code null}. */
    String getNotes();
}
