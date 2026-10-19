package com.menta.billing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * JPA row of {@code billing_purchases}: one fulfillment record per payment.
 *
 * <p>{@code payment_id} is unique, the status is stored as the {@code FulfillmentStatus} name, and
 * the admin resolution record (#539) is flattened into four nullable columns, and the ordered
 * session ids live in the child table {@code billing_purchase_sessions} (see {@link
 * PurchaseSessionJpaEntity}) instead of a relationship on this entity.
 */
@Entity
@Table(name = "billing_purchases")
public class PurchaseJpaEntity {

    @jakarta.persistence.Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payment_id", columnDefinition = "BINARY(16)", nullable = false, unique = true)
    private UUID paymentId;

    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(20)")
    private String status;

    /** Admin resolution (#539): {@code PurchaseResolutionType} name, set only when status is RESOLVED. */
    @Getter
    @Column(name = "resolution", columnDefinition = "VARCHAR(20)")
    private String resolution;

    @Getter
    @Column(name = "resolution_note", columnDefinition = "VARCHAR(500)")
    private String resolutionNote;

    @Getter
    @Column(name = "resolved_by", columnDefinition = "BINARY(16)")
    private UUID resolvedBy;

    @Getter
    @Column(name = "resolved_at", columnDefinition = "DATETIME(3)")
    private Instant resolvedAt;

    protected PurchaseJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public PurchaseJpaEntity(UUID id, UUID paymentId, String status) {
        this.id = id;
        this.paymentId = paymentId;
        this.status = status;
    }

    public PurchaseJpaEntity(
        UUID id, UUID paymentId, String status, String resolution, String resolutionNote, UUID resolvedBy,
        Instant resolvedAt
    ) {
        this(id, paymentId, status);
        this.resolution = resolution;
        this.resolutionNote = resolutionNote;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getStatus() {
        return status;
    }
}
