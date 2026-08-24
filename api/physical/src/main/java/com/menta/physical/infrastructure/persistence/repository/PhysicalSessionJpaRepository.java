package com.menta.physical.infrastructure.persistence.repository;

import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhysicalSessionJpaRepository extends JpaRepository<PhysicalSessionJpaEntity, UUID> {

    /**
     * {@code assignedSpots}/{@code activeCapacityHolds} are correlated
     * subqueries evaluated at query time — a live COUNT against committed
     * rows, never a cached counter (see {@code PhysicalSession}'s Javadoc).
     * A hold counts only while {@code expires_at > :now}. Public-safe: only
     * {@code SCHEDULED} sessions (US-PHYSICAL-006) — a cancelled session
     * must never be offered as available to an external visitor.
     */
    @Query(
        value = "SELECT s.id AS id, s.course_id AS courseId, s.scheduled_at AS scheduledAt, "
            + "s.capacity AS capacity, "
            + "(SELECT COUNT(*) FROM physical_capacity_assignments a WHERE a.session_id = s.id) "
            + "AS assignedSpots, "
            + "(SELECT COUNT(*) FROM physical_capacity_holds h "
            + "WHERE h.session_id = s.id AND h.expires_at > :now) AS activeCapacityHolds, "
            + "s.status AS status, s.notes AS notes "
            + "FROM physical_sessions s "
            + "WHERE s.course_id = :courseId AND s.scheduled_at BETWEEN :from AND :to "
            + "AND s.status = 'SCHEDULED' "
            + "ORDER BY s.scheduled_at ASC",
        nativeQuery = true
    )
    List<PhysicalSessionAvailabilityProjection> findScheduledWithAvailability(
        @Param("courseId") UUID courseId,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("now") Instant now
    );

    /**
     * Management read (US-PHYSICAL-006 escenario 3): every status, unlike
     * {@link #findScheduledWithAvailability} above.
     */
    @Query(
        value = "SELECT s.id AS id, s.course_id AS courseId, s.scheduled_at AS scheduledAt, "
            + "s.capacity AS capacity, "
            + "(SELECT COUNT(*) FROM physical_capacity_assignments a WHERE a.session_id = s.id) "
            + "AS assignedSpots, "
            + "(SELECT COUNT(*) FROM physical_capacity_holds h "
            + "WHERE h.session_id = s.id AND h.expires_at > :now) AS activeCapacityHolds, "
            + "s.status AS status, s.notes AS notes "
            + "FROM physical_sessions s "
            + "WHERE s.course_id = :courseId AND s.scheduled_at BETWEEN :from AND :to "
            + "ORDER BY s.scheduled_at ASC",
        nativeQuery = true
    )
    List<PhysicalSessionAvailabilityProjection> findManagedWithAvailability(
        @Param("courseId") UUID courseId,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("now") Instant now
    );

    /** Single-session read with the same live availability computation, for update flows. */
    @Query(
        value = "SELECT s.id AS id, s.course_id AS courseId, s.scheduled_at AS scheduledAt, "
            + "s.capacity AS capacity, "
            + "(SELECT COUNT(*) FROM physical_capacity_assignments a WHERE a.session_id = s.id) "
            + "AS assignedSpots, "
            + "(SELECT COUNT(*) FROM physical_capacity_holds h "
            + "WHERE h.session_id = s.id AND h.expires_at > :now) AS activeCapacityHolds, "
            + "s.status AS status, s.notes AS notes "
            + "FROM physical_sessions s "
            + "WHERE s.id = :sessionId",
        nativeQuery = true
    )
    Optional<PhysicalSessionAvailabilityProjection> findByIdWithAvailability(
        @Param("sessionId") UUID sessionId, @Param("now") Instant now
    );

    /**
     * TASK-005: pessimistic-lock variant of {@link #findByIdWithAvailability}
     * for the capacity-assignment concurrency contract.  The
     * {@code SELECT ... FOR UPDATE} acquires an exclusive row lock on
     * {@code physical_sessions} for the duration of the transaction, so a
     * concurrent handler's serialised reads see the post-commit count.
     * Without this lock, two threads can both observe
     * {@code assignedSpots = 0 < capacity = 1} and both INSERT successfully
     * (V7 {@code UNIQUE} only blocks SAME-pair duplicates).
     */
    @Query(
        value = "SELECT s.id AS id, s.course_id AS courseId, s.scheduled_at AS scheduledAt, "
            + "s.capacity AS capacity, "
            + "(SELECT COUNT(*) FROM physical_capacity_assignments a WHERE a.session_id = s.id) "
            + "AS assignedSpots, "
            + "(SELECT COUNT(*) FROM physical_capacity_holds h "
            + "WHERE h.session_id = s.id AND h.expires_at > :now) AS activeCapacityHolds, "
            + "s.status AS status, s.notes AS notes "
            + "FROM physical_sessions s "
            + "WHERE s.id = :sessionId "
            + "FOR UPDATE",
        nativeQuery = true
    )
    Optional<PhysicalSessionAvailabilityProjection> findByIdWithAvailabilityForUpdate(
        @Param("sessionId") UUID sessionId, @Param("now") Instant now
    );

    /**
     * TASK-005: light-weight pessimistic-only lock for use cases that
     * need the row lock but not the availability projection. Combined with
     * {@link com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository#countBySessionId}, the adapter
     * holds the row lock when re-counting, so its {@code SELECT COUNT(*)}
     * reads the post-commit sum across peer transactions.
     */
    @Query(value = "SELECT id FROM physical_sessions WHERE id = :sessionId FOR UPDATE", nativeQuery = true)
    java.util.List<java.util.UUID> lockSessionRow(@Param("sessionId") UUID sessionId);

    /** US-PHYSICAL-005 escenario 4: guards course deactivation. */
    @Query(
        value = "SELECT EXISTS("
            + "SELECT 1 FROM physical_sessions s "
            + "JOIN physical_capacity_assignments a ON a.session_id = s.id "
            + "WHERE s.course_id = :courseId AND s.scheduled_at > :now"
            + ")",
        nativeQuery = true
    )
    boolean existsFutureAssignedSession(@Param("courseId") UUID courseId, @Param("now") Instant now);
}
