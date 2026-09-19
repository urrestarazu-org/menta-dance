package com.menta.billing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence model for {@code billing_payment_proofs} (#31, US-BILLING-003, design C5).
 *
 * <p>{@code payment_id} carries the schema's unique key: a replacement proof overwrites the one
 * existing row for its payment, so this entity holds no relation back to {@code Payment} — the
 * two tables are joined only on the raw column value, exactly like {@code PaymentJpaEntity}
 * carries no relations of its own.</p>
 */
@Entity
@Table(name = "billing_payment_proofs")
public class PaymentProofJpaEntity {

    @jakarta.persistence.Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payment_id", columnDefinition = "BINARY(16)", nullable = false, unique = true, updatable = false)
    private UUID paymentId;

    @Column(name = "storage_key", nullable = false, length = 160)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected PaymentProofJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public PaymentProofJpaEntity(
        UUID id, UUID paymentId, String storageKey, String originalFilename, String contentType,
        long sizeBytes, Instant uploadedAt
    ) {
        this.id = id;
        this.paymentId = paymentId;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
