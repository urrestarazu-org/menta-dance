package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.PaymentJpaEntity;
import com.menta.billing.infrastructure.persistence.projection.PendingVerificationRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository over {@code billing_payments}.
 *
 * <p>Used only through {@code PaymentRepositoryAdapter}. Besides the lookups by unique column it
 * hosts the two paged queries behind the expiry sweep and the admin verification inbox.
 */
public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, UUID> {

    /** Finds the payment bound to a provider payment id; the column is unique, so at most one row matches. */
    Optional<PaymentJpaEntity> findByProviderPaymentId(String providerPaymentId);

    /**
     * Finds the payment by the external reference generated at checkout; the column is unique, so
     * at most one row matches.
     */
    Optional<PaymentJpaEntity> findByExpectedExternalReference(String expectedExternalReference);

    /**
     * Id-only projection for the 72h automatic expiry sweep (#31, US-BILLING-003, design C6). A
     * submitted proof withholds automatic expiry — the {@code NOT EXISTS} against {@code
     * billing_payment_proofs} is the spec's exact condition. Served by the existing {@code
     * idx_billing_payments_status_created (status_type, created_at)} index (V20_1_5) — no new
     * index needed.
     */
    @Query("SELECT p.id FROM PaymentJpaEntity p WHERE p.statusType = 'AWAITING_MANUAL_VERIFICATION' "
        + "AND p.createdAt < :createdBefore AND NOT EXISTS "
        + "(SELECT 1 FROM PaymentProofJpaEntity pr WHERE pr.paymentId = p.id)")
    List<UUID> findExpirableBankTransferIds(@Param("createdBefore") Instant createdBefore, Pageable pageable);

    /**
     * The admin pending-verification inbox (#33, US-BILLING-005, design C10). An explicit {@code
     * countQuery} is mandatory — Spring Data cannot derive a count from a constructor expression.
     * {@code proofCount} is a correlated {@code COUNT} subquery, avoiding a join that would
     * multiply rows. Served by the existing {@code idx_billing_payments_status_created
     * (status_type, created_at)} (V20_1_5) — no new index needed.
     */
    @Query(value =
        """
        SELECT new com.menta.billing.infrastructure.persistence.projection.PendingVerificationRow(
            p.id, p.userId, p.targetModality, p.targetReference, p.expectedAmount, p.expectedCurrency,
            p.statusType, p.createdAt,
            (SELECT COUNT(pr) FROM PaymentProofJpaEntity pr WHERE pr.paymentId = p.id))
          FROM PaymentJpaEntity p
         WHERE p.statusType = :statusType
         ORDER BY p.createdAt ASC
        """,
        countQuery = "SELECT COUNT(p) FROM PaymentJpaEntity p WHERE p.statusType = :statusType")
    Page<PendingVerificationRow> findByStatusTypeOrderByCreatedAt(
        @Param("statusType") String statusType, Pageable pageable);
}
