package com.menta.billing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** JPA persistence model for the billing_physical_course_quotes table. */
@Entity
@Table(name = "billing_physical_course_quotes")
public class PhysicalCourseQuoteJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "course_id", columnDefinition = "VARCHAR(64)", nullable = false, updatable = false)
    private String courseId;

    @Column(name = "purchase_type", columnDefinition = "VARCHAR(20)", nullable = false, updatable = false)
    private String purchaseType;

    @Column(name = "monthly_price_amount", nullable = false, updatable = false, columnDefinition = "DECIMAL(10,2)")
    private BigDecimal monthlyPriceAmount;

    @Column(
        name = "monthly_price_currency", nullable = false, updatable = false, columnDefinition = "CHAR(3)"
    )
    private String monthlyPriceCurrency;

    @Column(
        name = "individual_surcharge_percent", nullable = false, updatable = false,
        columnDefinition = "DECIMAL(5,2)"
    )
    private BigDecimal individualSurchargePercent;

    @Column(name = "pricing_version", nullable = false, updatable = false)
    private int pricingVersion;

    @Column(name = "scheduled_session_count", nullable = false, updatable = false)
    private int scheduledSessionCount;

    @Column(name = "selected_session_id", columnDefinition = "VARCHAR(64)", updatable = false)
    private String selectedSessionId;

    @Column(name = "amount_value", nullable = false, updatable = false, columnDefinition = "DECIMAL(10,2)")
    private BigDecimal amountValue;

    @Column(name = "amount_currency", nullable = false, updatable = false, columnDefinition = "CHAR(3)")
    private String amountCurrency;

    @Column(name = "availability", columnDefinition = "VARCHAR(20)", nullable = false, updatable = false)
    private String availability;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected PhysicalCourseQuoteJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public PhysicalCourseQuoteJpaEntity(
        String id, String courseId, String purchaseType, BigDecimal monthlyPriceAmount, String monthlyPriceCurrency,
        BigDecimal individualSurchargePercent, int pricingVersion, int scheduledSessionCount,
        String selectedSessionId, BigDecimal amountValue, String amountCurrency, String availability,
        Instant createdAt, Instant expiresAt
    ) {
        this.id = id;
        this.courseId = courseId;
        this.purchaseType = purchaseType;
        this.monthlyPriceAmount = monthlyPriceAmount;
        this.monthlyPriceCurrency = monthlyPriceCurrency;
        this.individualSurchargePercent = individualSurchargePercent;
        this.pricingVersion = pricingVersion;
        this.scheduledSessionCount = scheduledSessionCount;
        this.selectedSessionId = selectedSessionId;
        this.amountValue = amountValue;
        this.amountCurrency = amountCurrency;
        this.availability = availability;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getId() {
        return id;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getPurchaseType() {
        return purchaseType;
    }

    public BigDecimal getMonthlyPriceAmount() {
        return monthlyPriceAmount;
    }

    public String getMonthlyPriceCurrency() {
        return monthlyPriceCurrency;
    }

    public BigDecimal getIndividualSurchargePercent() {
        return individualSurchargePercent;
    }

    public int getPricingVersion() {
        return pricingVersion;
    }

    public int getScheduledSessionCount() {
        return scheduledSessionCount;
    }

    public String getSelectedSessionId() {
        return selectedSessionId;
    }

    public BigDecimal getAmountValue() {
        return amountValue;
    }

    public String getAmountCurrency() {
        return amountCurrency;
    }

    public String getAvailability() {
        return availability;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
