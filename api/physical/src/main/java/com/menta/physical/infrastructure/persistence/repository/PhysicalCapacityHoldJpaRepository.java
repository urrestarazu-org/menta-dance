package com.menta.physical.infrastructure.persistence.repository;

import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityHoldJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Housekeeping GC (#208, design B5, tasks Phase 9) — rows that expired
     * and were never converted. The invariant already ignores these at read
     * time ({@code countActiveBySessionIdForUpdate} above and
     * {@code PhysicalSessionJpaRepository}'s availability queries both filter
     * {@code expires_at > :now}), so this delete has no correctness effect;
     * it only stops the table growing forever. Bounded with {@code LIMIT} so
     * one sweep tick never holds row locks on an unbounded batch — sized by
     * {@code physical.capacity.hold.expiry.batch-size}, the sibling of
     * {@code billing.subscription.expiry.batch-size}.
     */
    @Modifying
    @Query(
        value = "DELETE FROM physical_capacity_holds "
            + "WHERE converted_at IS NULL AND expires_at <= :now "
            + "LIMIT :batchSize",
        nativeQuery = true
    )
    int deleteExpiredUnconverted(@Param("now") Instant now, @Param("batchSize") int batchSize);

    /**
     * Housekeeping GC (#208, design B5, tasks Phase 9) — rows already
     * converted (their {@code converted_at} set by the adapter's
     * {@code markConverted}) long enough ago ({@code
     * physical.capacity.hold.ttl-ms}, the same TTL that bounds a live hold's
     * lifetime) that they are pure history nobody reads:
     * {@link #findByPaymentIdOrdered} is only ever consulted during
     * conversion itself, never afterward.
     */
    @Modifying
    @Query(
        value = "DELETE FROM physical_capacity_holds "
            + "WHERE converted_at IS NOT NULL AND converted_at <= :cutoff "
            + "LIMIT :batchSize",
        nativeQuery = true
    )
    int deleteConvertedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
