package com.menta.billing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** JPA persistence model for the billing_physical_course_pricing table. */
@Entity
@Table(name = "billing_physical_course_pricing")
public class PhysicalCoursePricingJpaEntity {

    @Id
    @Column(name = "course_id", columnDefinition = "VARCHAR(64)", updatable = false, nullable = false)
    private String courseId;

    @Column(name = "monthly_price", nullable = false, columnDefinition = "DECIMAL(10,2)")
    private BigDecimal monthlyPrice;

    @Column(name = "monthly_price_currency", nullable = false, columnDefinition = "CHAR(3)")
    private String monthlyPriceCurrency;

    @Column(name = "individual_surcharge_percent", nullable = false, columnDefinition = "DECIMAL(5,2)")
    private BigDecimal individualSurchargePercent;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PhysicalCoursePricingJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public PhysicalCoursePricingJpaEntity(
        String courseId, BigDecimal monthlyPrice, String monthlyPriceCurrency, BigDecimal individualSurchargePercent,
        int version, Instant updatedAt
    ) {
        this.courseId = courseId;
        this.monthlyPrice = monthlyPrice;
        this.monthlyPriceCurrency = monthlyPriceCurrency;
        this.individualSurchargePercent = individualSurchargePercent;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    public String getCourseId() {
        return courseId;
    }

    public BigDecimal getMonthlyPrice() {
        return monthlyPrice;
    }

    public String getMonthlyPriceCurrency() {
        return monthlyPriceCurrency;
    }

    public BigDecimal getIndividualSurchargePercent() {
        return individualSurchargePercent;
    }

    public int getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
