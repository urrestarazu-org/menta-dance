package com.menta.billing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity for the append-only {@code billing_audit_log} table (#33, US-BILLING-005, design
 * D4/C7): one row per admin decision (approve/reject/correction), mirroring {@code
 * PhysicalDeviceAuditJpaEntity}'s shape. New mutable/constructor-injected class, so it uses
 * Lombok per CLAUDE.md's Boilerplate rule (D7) instead of hand-written accessors.
 */
@Entity
@Table(name = "billing_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BillingAuditLogJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payment_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "admin_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID adminId;

    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @Column(name = "reason", columnDefinition = "TEXT", updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
