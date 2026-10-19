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
 *
 * <p>{@code provider_payment_id} is nullable and {@code
 * expected_external_reference} is the unique correlation key
 * (US-BILLING-010): a Checkout Pro payment has no provider id until the buyer
 * actually pays. MySQL's UNIQUE indexes admit any number of NULLs, so the
 * uniqueness of the non-null ids survives the change.</p>
 */
@Entity
@Table(name = "billing_payments")
public class PaymentJpaEntity {

    @jakarta.persistence.Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "provider_payment_id", unique = true)
    private String providerPaymentId;

    @Column(name = "expected_amount", nullable = false, columnDefinition = "DECIMAL(10,2)")
    private BigDecimal expectedAmount;

    @Column(name = "expected_currency", nullable = false, columnDefinition = "CHAR(3)")
    private String expectedCurrency;

    @Column(name = "expected_external_reference", nullable = false, unique = true)
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
        UUID id, UUID userId, String providerPaymentId, BigDecimal expectedAmount, String expectedCurrency,
        String expectedExternalReference, String expectedMerchantAccountId, String targetModality,
        String targetReference, String statusType, String statusReason, Instant statusChangedAt,
        Instant createdAt
    ) {
        this.id = id;
        this.userId = userId;
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

    public UUID getUserId() {
        return userId;
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
