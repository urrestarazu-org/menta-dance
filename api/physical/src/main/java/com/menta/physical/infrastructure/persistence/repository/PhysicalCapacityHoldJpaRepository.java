package com.menta.physical.infrastructure.persistence.repository;

import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityHoldJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Schema/seeding repository plus the hold write path (#208, US-PHYSICAL-004b)
 * — see {@link PhysicalCapacityHoldJpaEntity}'s Javadoc.
 */
public interface PhysicalCapacityHoldJpaRepository extends JpaRepository<PhysicalCapacityHoldJpaEntity, UUID> {

    /**
     * Step 3 of the hold claim (design B4): the count that the hold
     * invariant is decided on, as a LOCKING read — the sibling of
     * {@link PhysicalCapacityAssignmentJpaRepository#countBySessionIdForUpdate}
     * for the {@code physical_capacity_holds} table.
     *
     * <p>Only counts holds that are still active: {@code converted_at IS
     * NULL} (not yet converted to an assignment — see design B3) and
     * {@code expires_at > :now} (not yet expired — design B1/B5). Served by
     * {@code idx_physical_holds_session_expires}, so the gap lock is scoped
     * to one session's index range.</p>
     */
    @Query(
        value = "SELECT COUNT(*) FROM physical_capacity_holds h "
            + "WHERE h.session_id = :sessionId AND h.converted_at IS NULL "
            + "AND h.expires_at > :now FOR UPDATE",
        nativeQuery = true
    )
    long countActiveBySessionIdForUpdate(@Param("sessionId") UUID sessionId, @Param("now") Instant now);

    /**
     * Every hold row for one payment, ordered by its session's
     * {@code scheduled_at} then {@code session_id} — the same total order
     * {@link com.menta.shared.physical.SessionClaimOrdering} enforces on the
     * claim list that produced these rows. Used by release (all rows for a
     * payment) and, from design step 8, by conversion (mark-then-assert in
     * the same claim order).
     */
    @Query(
        value = "SELECT h.* FROM physical_capacity_holds h "
            + "JOIN physical_sessions s ON s.id = h.session_id "
            + "WHERE h.payment_id = :paymentId "
            + "ORDER BY s.scheduled_at ASC, h.session_id ASC",
        nativeQuery = true
    )
    List<PhysicalCapacityHoldJpaEntity> findByPaymentIdOrdered(@Param("paymentId") UUID paymentId);
}
