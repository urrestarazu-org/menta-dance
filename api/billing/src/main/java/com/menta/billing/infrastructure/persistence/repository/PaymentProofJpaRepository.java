package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.PaymentProofJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProofJpaRepository extends JpaRepository<PaymentProofJpaEntity, UUID> {

    Optional<PaymentProofJpaEntity> findByPaymentId(UUID paymentId);
}
