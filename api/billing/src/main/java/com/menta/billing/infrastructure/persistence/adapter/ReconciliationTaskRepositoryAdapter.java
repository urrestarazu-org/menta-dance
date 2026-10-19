package com.menta.billing.infrastructure.persistence.adapter;

import com.menta.billing.application.port.out.ReconciliationTaskRepository;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.infrastructure.persistence.repository.ReconciliationTaskJpaRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter resolving the {@code billing_reconciliation_tasks} row tied to a payment (#33,
 * US-BILLING-005, design D2/C11). {@code REQUIRED} — the same reasoning as {@link
 * PaymentAuditRepositoryAdapter}: this write and the correction's status transition are one
 * atomic fact.
 */
@Component
@RequiredArgsConstructor
public class ReconciliationTaskRepositoryAdapter implements ReconciliationTaskRepository {

    private final ReconciliationTaskJpaRepository reconciliationTaskJpaRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public int resolveOpenByPaymentId(PaymentId paymentId, Instant at, UUID adminId) {
        return reconciliationTaskJpaRepository.resolveOpenByPaymentId(paymentId.getValue(), at, adminId);
    }
}
