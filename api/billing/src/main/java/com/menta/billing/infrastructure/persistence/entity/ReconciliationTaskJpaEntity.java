package com.menta.billing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for {@code billing_reconciliation_tasks}. {@code paymentId} is
 * nullable on purpose: when the inbox exhausts retries or finds a mismatch
 * with no local {@code Payment} for the {@code providerPaymentId}, the task
 * references the provider event directly instead of inventing a Payment
 * (US-BILLING-002 — "si no existe pago local... referencia el inbox/evento").
 */
@Entity
@Table(name = "billing_reconciliation_tasks")
public class ReconciliationTaskJpaEntity {

    @jakarta.persistence.Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payment_id", columnDefinition = "BINARY(16)")
    private UUID paymentId;

    @Column(name = "provider_payment_id", nullable = false)
    private String providerPaymentId;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReconciliationTaskJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public ReconciliationTaskJpaEntity(
        UUID id, UUID paymentId, String providerPaymentId, String reason, Instant createdAt
    ) {
        this.id = id;
        this.paymentId = paymentId;
        this.providerPaymentId = providerPaymentId;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
