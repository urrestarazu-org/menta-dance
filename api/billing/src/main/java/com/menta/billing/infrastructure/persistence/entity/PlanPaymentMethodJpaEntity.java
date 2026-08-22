package com.menta.billing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/**
 * A payment rail a plan accepts (US-BILLING-010 escenario 4b).
 *
 * <p>Its own table rather than a column on {@code billing_plans}, mirroring
 * {@code billing_plan_courses}: a plan accepts a <em>set</em>, and a
 * comma-joined string column would have to be parsed on every read and could
 * not be constrained.</p>
 */
@Entity
@Table(
    name = "billing_plan_payment_methods",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_billing_plan_payment_methods", columnNames = {"plan_id", "payment_method"}
    )
)
public class PlanPaymentMethodJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "plan_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "payment_method", nullable = false, updatable = false, columnDefinition = "VARCHAR(30)")
    private String paymentMethod;

    protected PlanPaymentMethodJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public PlanPaymentMethodJpaEntity(UUID planId, String paymentMethod) {
        this.planId = planId;
        this.paymentMethod = paymentMethod;
    }

    public Long getId() {
        return id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }
}
