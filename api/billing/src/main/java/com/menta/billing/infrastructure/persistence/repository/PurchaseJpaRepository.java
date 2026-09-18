package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.PurchaseJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseJpaRepository extends JpaRepository<PurchaseJpaEntity, UUID> {

    /**
     * A PLAIN consistent read — deliberately non-locking. This is the
     * pre-insert existence check {@code CreatePurchaseFromPaymentEventUseCase}
     * runs BEFORE possibly calling {@code
     * PurchaseRepositoryAdapter#saveIsolated}. Locking this read (#245) would
     * self-block that very insert: InnoDB's {@code FOR UPDATE} on a
     * non-existent {@code payment_id} takes a gap lock on the surrounding
     * index range, and the isolated {@code REQUIRES_NEW} sub-transaction's
     * {@code INSERT} into that same gap — on a DIFFERENT connection, from the
     * SAME calling thread — would then have to wait for an insert-intention
     * lock that only THIS transaction can release, which it never does
     * because it is itself blocked waiting for {@code saveIsolated} to
     * return. Measured: every happy-path confirmation test failed with
     * "Lock wait timeout exceeded" once this method was made a locking read.
     */
    Optional<PurchaseJpaEntity> findByPaymentId(UUID paymentId);

    /**
     * A LOCKING read (#245) — the sibling of {@code
     * PhysicalCapacityHoldJpaRepository#countActiveBySessionIdForUpdate} and
     * the #216 lesson documented on {@code AssignCapacityUseCase}: under
     * REPEATABLE READ, a plain consistent read fixes this transaction's MVCC
     * snapshot at the point it runs, so it would silently miss a row {@code
     * PurchaseRepositoryAdapter#saveIsolated} committed moments earlier on a
     * genuinely different connection. Used only where no insert
     * into the SAME row's gap follows in this transaction, so — unlike {@link
     * #findByPaymentId(UUID)} — there is no self-block risk here: {@code
     * CreatePurchaseFromPaymentEventUseCase}'s own post-{@code saveIsolated}
     * fallback (the row already exists, committed by the race's winner) and
     * {@code MarkPurchaseAssignedUseCase}/{@code MarkPurchaseExceptionUseCase}
     * (an {@code UPDATE} of an existing row, not an {@code INSERT}).
     */
    @Query(
        value = "SELECT * FROM billing_purchases WHERE payment_id = :paymentId FOR UPDATE",
        nativeQuery = true
    )
    Optional<PurchaseJpaEntity> findByPaymentIdForUpdate(@Param("paymentId") UUID paymentId);
}
