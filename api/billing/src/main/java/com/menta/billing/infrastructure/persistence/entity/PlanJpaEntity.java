package com.menta.billing.infrastructure.persistence.entity;

import com.menta.billing.domain.model.PlanStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for the billing_plans table. */
@Entity
@Table(name = "billing_plans")
public class PlanJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false, columnDefinition = "DECIMAL(10,2)")
    private BigDecimal price;

    @Column(name = "currency", nullable = false, columnDefinition = "CHAR(3)")
    private String currency;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "featured", nullable = false)
    private boolean featured;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(20)")
    private PlanStatus status;

    @Column(name = "terms_and_conditions", nullable = false, columnDefinition = "TEXT")
    private String termsAndConditions;

    @Column(name = "cancellation_policy", nullable = false, columnDefinition = "TEXT")
    private String cancellationPolicy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlanJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public PlanJpaEntity(
        UUID id, String name, String description, BigDecimal price, String currency,
        int durationDays, boolean featured, PlanStatus status, String termsAndConditions,
        String cancellationPolicy, Instant createdAt, Instant updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.durationDays = durationDays;
        this.featured = featured;
        this.status = status;
        this.termsAndConditions = termsAndConditions;
        this.cancellationPolicy = cancellationPolicy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public boolean isFeatured() {
        return featured;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public String getTermsAndConditions() {
        return termsAndConditions;
    }

    public String getCancellationPolicy() {
        return cancellationPolicy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
