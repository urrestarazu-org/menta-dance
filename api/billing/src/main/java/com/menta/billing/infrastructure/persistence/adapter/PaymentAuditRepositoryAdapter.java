package com.menta.billing.infrastructure.persistence.adapter;

import com.menta.billing.application.port.out.PaymentAuditRepository;
import com.menta.billing.domain.model.PaymentAuditAction;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.infrastructure.persistence.entity.BillingAuditLogJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.BillingAuditLogJpaRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for the payment audit trail (#33, US-BILLING-005, design D4/C7), mirroring {@code
 * PhysicalDeviceAuditRepositoryAdapter}'s shape. {@code REQUIRED} — not {@code REQUIRES_NEW} — is
 * deliberate: the audit row and the payment's status transition are one atomic fact, so neither
 * can exist without the other (D8's rollback-safety concern, answered by making them the same
 * write).
 */
@Component
@RequiredArgsConstructor
public class PaymentAuditRepositoryAdapter implements PaymentAuditRepository {

    private final BillingAuditLogJpaRepository auditRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void append(PaymentId paymentId, UUID adminId, PaymentAuditAction action, String reason) {
        auditRepository.save(new BillingAuditLogJpaEntity(
            UUID.randomUUID(), paymentId.getValue(), adminId, action.name(), reason, Instant.now()
        ));
    }
}
