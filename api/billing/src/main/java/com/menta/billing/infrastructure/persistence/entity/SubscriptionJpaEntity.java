package com.menta.billing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence model for {@code billing_subscriptions} (US-BILLING-010).
 *
 * <p>Two unique constraints carry the concurrency guarantees, neither is an
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
 *
 * <p>{@code payment_id} is nullable since V18 (US-BILLING-012, design A1): a
 * {@code TRIAL} row has no {@code Payment}. {@code type} and the four {@code
 * TrialGrant} columns are written together, exactly once, by {@code
 * Subscription.trial(...)} — same discipline as the cancellation columns
 * added in V17. {@code version} backs the optimistic lock the automatic
 * expiry sweep needs (design A14): a {@code long} <strong>primitive</strong>,
 * not a wrapper — a wrapper would make Spring Data's {@code isNew()} inspect
 * the version instead of the id and call {@code persist()} on an update.</p>
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

    @Column(name = "payment_id", columnDefinition = "BINARY(16)", unique = true)
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

    @Column(name = "type", nullable = false, columnDefinition = "VARCHAR(20)")
    private String type;

    /** NULL unless {@code type = TRIAL} — the trial grant audit trail (US-BILLING-012). */
    @Column(name = "granted_at")
    private Instant grantedAt;

    @Column(name = "granted_by", columnDefinition = "BINARY(16)")
    private UUID grantedBy;

    @Column(name = "grant_reason", length = 500)
    private String grantReason;

    @Column(name = "grant_days")
    private Integer grantDays;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected SubscriptionJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public SubscriptionJpaEntity(
        UUID id, UUID paymentId, UUID userId, UUID planId, String idempotencyKey, UUID activeUserId,
        String status, String fulfillmentStatus, Instant startDate, Instant endDate,
        String providerPreferenceId, String checkoutUrl, Instant createdAt,
        Instant cancelledAt, UUID cancelledBy, String cancellationReason,
        String type, Instant grantedAt, UUID grantedBy, String grantReason, Integer grantDays, long version
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
        this.type = type;
        this.grantedAt = grantedAt;
        this.grantedBy = grantedBy;
        this.grantReason = grantReason;
        this.grantDays = grantDays;
        this.version = version;
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

    public String getType() {
        return type;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }

    public String getGrantReason() {
        return grantReason;
    }

    public Integer getGrantDays() {
        return grantDays;
    }

    public long getVersion() {
        return version;
    }
}
