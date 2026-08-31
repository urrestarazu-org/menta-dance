package com.menta.billing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence model for {@code billing_subscriptions} (US-BILLING-010).
 *
 * <p>Two unique constraints carry the concurrency guarantees; neither is an
 * exists-then-insert check, both are decided by the database:</p>
 * <ul>
 *   <li>{@code (user_id, idempotency_key)} — escenario 5.</li>
 *   <li>{@code active_user_id} — at most one subscription may occupy a user's
 *       slot. MySQL has no partial indexes, so the column is an
 *       application-maintained projection of {@code user_id}: set while the
 *       status is {@code PENDING}/{@code ACTIVE}, NULL once the subscription
 *       terminates. A UNIQUE index admits any number of NULLs, so terminated
 *       subscriptions stop blocking a new checkout without being deleted. See
 *       {@code SubscriptionJpaMapper}, its single writer.</li>
 * </ul>
 *
 * <p>The course snapshot lives in {@code billing_subscription_courses} — see
 * {@link SubscriptionCourseJpaEntity} for why it cannot be derived from
 * {@code billing_plan_courses}.</p>
 */
@Entity
@Table(
    name = "billing_subscriptions",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_billing_subscriptions_idempotency", columnNames = {"user_id", "idempotency_key"}
        ),
        @UniqueConstraint(name = "uq_billing_subscriptions_active_user", columnNames = {"active_user_id"})
    }
)
public class SubscriptionJpaEntity {

    @jakarta.persistence.Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payment_id", columnDefinition = "BINARY(16)", nullable = false, unique = true)
    private UUID paymentId;

    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "plan_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    /** NULL once the subscription no longer occupies the user's slot — see this class's Javadoc. */
    @Column(name = "active_user_id", columnDefinition = "BINARY(16)")
    private UUID activeUserId;

    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(20)")
    private String status;

    @Column(name = "fulfillment_status", nullable = false, columnDefinition = "VARCHAR(30)")
    private String fulfillmentStatus;

    @Column(name = "start_date")
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "provider_preference_id", length = 128)
    private String providerPreferenceId;

    @Column(name = "checkout_url", length = 512)
    private String checkoutUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** NULL unless {@code cancel()} has run — the cancellation audit trail (US-BILLING-011). */
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by", columnDefinition = "BINARY(16)")
    private UUID cancelledBy;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    protected SubscriptionJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public SubscriptionJpaEntity(
        UUID id, UUID paymentId, UUID userId, UUID planId, String idempotencyKey, UUID activeUserId,
        String status, String fulfillmentStatus, Instant startDate, Instant endDate,
        String providerPreferenceId, String checkoutUrl, Instant createdAt,
        Instant cancelledAt, UUID cancelledBy, String cancellationReason
    ) {
        this.id = id;
        this.paymentId = paymentId;
        this.userId = userId;
        this.planId = planId;
        this.idempotencyKey = idempotencyKey;
        this.activeUserId = activeUserId;
        this.status = status;
        this.fulfillmentStatus = fulfillmentStatus;
        this.startDate = startDate;
        this.endDate = endDate;
        this.providerPreferenceId = providerPreferenceId;
        this.checkoutUrl = checkoutUrl;
        this.createdAt = createdAt;
        this.cancelledAt = cancelledAt;
        this.cancelledBy = cancelledBy;
        this.cancellationReason = cancellationReason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getActiveUserId() {
        return activeUserId;
    }

    public String getStatus() {
        return status;
    }

    public String getFulfillmentStatus() {
        return fulfillmentStatus;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public String getProviderPreferenceId() {
        return providerPreferenceId;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public UUID getCancelledBy() {
        return cancelledBy;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }
}
