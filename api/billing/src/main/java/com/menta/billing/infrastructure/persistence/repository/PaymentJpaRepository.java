package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.PaymentJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, UUID> {

    Optional<PaymentJpaEntity> findByProviderPaymentId(String providerPaymentId);

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
}
