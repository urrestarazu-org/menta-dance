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
