package com.menta.billing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "billing_purchases")
public class PurchaseJpaEntity {

    @jakarta.persistence.Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payment_id", columnDefinition = "BINARY(16)", nullable = false, unique = true)
    private UUID paymentId;

    @Column(name = "physical_session_id", nullable = false)
    private String physicalSessionId;

    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(20)")
    private String status;

    protected PurchaseJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public PurchaseJpaEntity(UUID id, UUID paymentId, String physicalSessionId, String status) {
        this.id = id;
        this.paymentId = paymentId;
        this.physicalSessionId = physicalSessionId;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getPhysicalSessionId() {
        return physicalSessionId;
    }

    public String getStatus() {
        return status;
    }
}
