package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.ReconciliationTaskJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReconciliationTaskJpaRepository extends JpaRepository<ReconciliationTaskJpaEntity, UUID> {

    /**
     * D2/C11 (#33, US-BILLING-005): resolves the open reconciliation task tied to a payment.
     * {@code AND resolved = false} makes a re-run idempotent. A return of {@code 0} is not an
     * error — {@code WebhookVerificationWorker} writes tasks with a {@code null} payment_id when
     * no local payment matched, so a payment can legitimately have no tied task.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE ReconciliationTaskJpaEntity t
           SET t.resolved = true, t.resolvedAt = :at, t.resolvedBy = :adminId
         WHERE t.paymentId = :paymentId AND t.resolved = false
        """)
    int resolveOpenByPaymentId(
        @Param("paymentId") UUID paymentId, @Param("at") Instant at, @Param("adminId") UUID adminId
    );
}
