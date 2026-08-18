package com.menta.billing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "billing_subscriptions")
public class SubscriptionJpaEntity {

    @jakarta.persistence.Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payment_id", columnDefinition = "BINARY(16)", nullable = false, unique = true)
    private UUID paymentId;

    @Column(name = "virtual_course_id", nullable = false)
    private String virtualCourseId;

    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(20)")
    private String status;

    protected SubscriptionJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public SubscriptionJpaEntity(UUID id, UUID paymentId, String virtualCourseId, String status) {
        this.id = id;
        this.paymentId = paymentId;
        this.virtualCourseId = virtualCourseId;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getVirtualCourseId() {
        return virtualCourseId;
    }

    public String getStatus() {
        return status;
    }
}
