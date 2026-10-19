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
 * One course a subscription bought, frozen at activation (US-BILLING-010
 * escenario 2b).
 *
 * <p>Deliberately <strong>not</strong> a view over {@code
 * billing_plan_courses}: that table holds the plan's <em>current</em>
 * composition, so deriving access from it would let an admin removing a
 * course cut off a student who already paid for it. Same by-value course id
 * discipline as everywhere else in Billing — never a FK into another module's
 * schema (docs/25-ARCHITECTURE-RULES.md).</p>
 */
@Entity
@Table(
    name = "billing_subscription_courses",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_billing_subscription_courses", columnNames = {"subscription_id", "course_id"}
    )
)
public class SubscriptionCourseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "subscription_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID subscriptionId;

    @Column(name = "course_id", nullable = false, updatable = false, length = 64)
    private String courseId;

    protected SubscriptionCourseJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public SubscriptionCourseJpaEntity(UUID subscriptionId, String courseId) {
        this.subscriptionId = subscriptionId;
        this.courseId = courseId;
    }

    public Long getId() {
        return id;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public String getCourseId() {
        return courseId;
    }
}
