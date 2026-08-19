package com.menta.physical.infrastructure.persistence.repository;

import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhysicalSessionJpaRepository extends JpaRepository<PhysicalSessionJpaEntity, UUID> {

    /**
     * {@code assignedSpots}/{@code activeCapacityHolds} are correlated
     * subqueries evaluated at query time — a live COUNT against committed
     * rows, never a cached counter (see {@code PhysicalSession}'s Javadoc).
     * A hold counts only while {@code expires_at > :now}.
     */
    @Query(
        value = "SELECT s.id AS id, s.course_id AS courseId, s.scheduled_at AS scheduledAt, "
            + "s.capacity AS capacity, "
            + "(SELECT COUNT(*) FROM physical_capacity_assignments a WHERE a.session_id = s.id) "
            + "AS assignedSpots, "
            + "(SELECT COUNT(*) FROM physical_capacity_holds h "
            + "WHERE h.session_id = s.id AND h.expires_at > :now) AS activeCapacityHolds "
            + "FROM physical_sessions s "
            + "WHERE s.course_id = :courseId AND s.scheduled_at BETWEEN :from AND :to "
            + "ORDER BY s.scheduled_at ASC",
        nativeQuery = true
    )
    List<PhysicalSessionAvailabilityProjection> findScheduledWithAvailability(
        @Param("courseId") UUID courseId,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("now") Instant now
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
