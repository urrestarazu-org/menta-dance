package com.menta.billing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA persistence model for {@code billing_purchase_sessions} (V20, design A1). One row per
 * session covered by a {@link PurchaseJpaEntity}, ordered by {@code position} — the claim order
 * design A2 mandates. Deliberately a flat child table, not a JPA {@code @OneToMany} on {@code
 * PurchaseJpaEntity} — same rationale as {@code PlanCourseJpaEntity}/{@code
 * PlanRepositoryAdapter}: a separate repository queried and joined in memory avoids lazy loading
 * and N+1 queries for a bounded per-purchase batch.
 */
@Entity
@Table(name = "billing_purchase_sessions")
public class PurchaseSessionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "purchase_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID purchaseId;

    @Column(name = "position", nullable = false, updatable = false)
    private int position;

    @Column(name = "physical_session_id", nullable = false, updatable = false)
    private String physicalSessionId;

    protected PurchaseSessionJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public PurchaseSessionJpaEntity(UUID purchaseId, int position, String physicalSessionId) {
        this.purchaseId = purchaseId;
        this.position = position;
        this.physicalSessionId = physicalSessionId;
    }

    public Long getId() {
        return id;
    }

    public UUID getPurchaseId() {
        return purchaseId;
    }

    public int getPosition() {
        return position;
    }

    public String getPhysicalSessionId() {
        return physicalSessionId;
    }
}
