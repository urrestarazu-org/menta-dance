package com.menta.billing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence model for {@code billing_payments}. {@code PaymentStatus}
 * (a sealed domain type) is flattened into {@code status_type} (discriminator)
 * plus two nullable columns that cover every variant's payload: {@code
 * status_reason} ({@code ReconciliationRequired}'s message) and {@code
 * status_changed_at} (the terminal states' instant — one column for all
 * four, since only one is ever set at a time). See {@code PaymentJpaMapper}.
 */
@Entity
@Table(name = "billing_payments")
public class PaymentJpaEntity {

    @jakarta.persistence.Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "provider_payment_id", nullable = false, unique = true)
    private String providerPaymentId;

    @Column(name = "expected_amount", nullable = false, columnDefinition = "DECIMAL(10,2)")
    private BigDecimal expectedAmount;

    @Column(name = "expected_currency", nullable = false, columnDefinition = "CHAR(3)")
    private String expectedCurrency;

    @Column(name = "expected_external_reference", nullable = false)
    private String expectedExternalReference;

    @Column(name = "expected_merchant_account_id", nullable = false)
    private String expectedMerchantAccountId;

    @Column(name = "target_modality", nullable = false, columnDefinition = "VARCHAR(20)")
    private String targetModality;

    @Column(name = "target_reference", nullable = false)
    private String targetReference;

    @Column(name = "status_type", nullable = false, columnDefinition = "VARCHAR(30)")
    private String statusType;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public PaymentJpaEntity(
        UUID id, String providerPaymentId, BigDecimal expectedAmount, String expectedCurrency,
        String expectedExternalReference, String expectedMerchantAccountId, String targetModality,
        String targetReference, String statusType, String statusReason, Instant statusChangedAt,
        Instant createdAt
    ) {
        this.id = id;
        this.providerPaymentId = providerPaymentId;
        this.expectedAmount = expectedAmount;
        this.expectedCurrency = expectedCurrency;
        this.expectedExternalReference = expectedExternalReference;
        this.expectedMerchantAccountId = expectedMerchantAccountId;
        this.targetModality = targetModality;
        this.targetReference = targetReference;
        this.statusType = statusType;
        this.statusReason = statusReason;
        this.statusChangedAt = statusChangedAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public String getExpectedCurrency() {
        return expectedCurrency;
    }

    public String getExpectedExternalReference() {
        return expectedExternalReference;
    }

    public String getExpectedMerchantAccountId() {
        return expectedMerchantAccountId;
    }

    public String getTargetModality() {
        return targetModality;
    }

    public String getTargetReference() {
        return targetReference;
    }

    public String getStatusType() {
        return statusType;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
