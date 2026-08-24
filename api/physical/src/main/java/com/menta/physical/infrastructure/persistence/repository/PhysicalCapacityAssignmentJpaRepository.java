package com.menta.physical.infrastructure.persistence.repository;

import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Schema/seeding repository — see {@link PhysicalCapacityAssignmentJpaEntity}'s Javadoc. */
public interface PhysicalCapacityAssignmentJpaRepository
    extends JpaRepository<PhysicalCapacityAssignmentJpaEntity, UUID> {

    /**
     * US-PHYSICAL-001 escenarios 1, 3, 7: a row here IS a confirmed
     * assignment — no status column to filter on (see {@link
     * PhysicalCapacityAssignmentJpaEntity}'s Javadoc and {@code
     * V7__physical_courses.sql}).
     *
     * <p>Derived query name, deliberately NOT a native {@code SELECT
     * EXISTS(...)} (unlike {@code PhysicalSessionJpaRepository
     * #existsFutureAssignedSession}): a native boolean-returning {@code
     * EXISTS(...)}/comparison expression comes back from MySQL as
     * {@code BIGINT}, which MySQL Connector/J maps to {@code Long}, not
     * {@code Boolean} — Spring Data's proxy then throws {@code
     * ClassCastException} coercing it to this method's {@code boolean}
     * return type. Confirmed against real MySQL via Testcontainers in
     * {@code PhysicalCheckInIntegrationTest}; a plain repository-mock unit
     * test never exercises the JDBC layer and cannot catch this. Spring
     * Data's own JPQL-derived exists-query has no such native-type
     * coercion step, so it is the safe shape here.</p>
     */
    boolean existsBySessionIdAndStudentId(UUID sessionId, UUID studentId);

    /**
     * TASK-005: re-reads the live count after a peer's INSERT commits, so
     * the adapter can fail-closed when the racing-INSERT pushed
     * {@code assignedSpots} above {@code capacity}. Spring Data derives
     * this from the method name without needing a hand-rolled
     * {@code @Query}.
     */
    long countBySessionId(UUID sessionId);
}
