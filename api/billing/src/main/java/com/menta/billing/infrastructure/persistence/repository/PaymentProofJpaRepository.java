package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.PaymentProofJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository over {@code billing_payment_proofs}.
 *
 * <p>Used only through {@code PaymentProofRepositoryAdapter}; it never leaves the infrastructure
 * layer.
 */
public interface PaymentProofJpaRepository extends JpaRepository<PaymentProofJpaEntity, UUID> {

    /** Finds the proof of a payment; {@code payment_id} is unique, so a payment has at most one proof row. */
    Optional<PaymentProofJpaEntity> findByPaymentId(UUID paymentId);
}
