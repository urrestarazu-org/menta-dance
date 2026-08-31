package com.menta.billing.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A user's subscription to a {@link Plan} (US-BILLING-010).
 *
 * <p>Carries two independent state axes on purpose:</p>
 * <ul>
 *   <li>{@link SubscriptionStatus} — the commercial lifecycle
 *       ({@code PENDING → ACTIVE → EXPIRED/CANCELLED}).</li>
 *   <li>{@link FulfillmentStatus} — whether Billing completed the local
 *       entitlement snapshot. Virtual reads that snapshot through the shared
 *       entitlement contract; it is not mutated from this aggregate
 *       (ADR-0039). Keeping this axis separate preserves recoverable states
 *       where a payment is completed but its snapshot is still exceptional.</li>
 * </ul>
 *
 * <p>{@code courseIds} is a <strong>snapshot</strong> taken at activation, not
 * a view over {@code billing_plan_courses}: escenario 2b requires that an
 * admin later editing or deactivating the plan never touches access already
 * paid for.</p>
 */
public final class Subscription {

    private final UUID id;
    private final PaymentId paymentId;
    private final UUID userId;
    private final PlanId planId;
    private final String idempotencyKey;
    private final SubscriptionStatus status;
    private final FulfillmentStatus fulfillmentStatus;
    private final Instant startDate;
    private final Instant endDate;
    private final List<String> courseIds;
    private final String providerPreferenceId;
    private final String checkoutUrl;
    private final Instant createdAt;
    private final Cancellation cancellation;

    public Subscription(
        UUID id, PaymentId paymentId, UUID userId, PlanId planId, String idempotencyKey,
        SubscriptionStatus status, FulfillmentStatus fulfillmentStatus, Instant startDate, Instant endDate,
        List<String> courseIds, String providerPreferenceId, String checkoutUrl, Instant createdAt,
        Cancellation cancellation
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.planId = Objects.requireNonNull(planId, "planId cannot be null");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey cannot be null or blank");
        }
        this.idempotencyKey = idempotencyKey;
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.fulfillmentStatus = Objects.requireNonNull(fulfillmentStatus, "fulfillmentStatus cannot be null");
        this.startDate = startDate;
        this.endDate = endDate;
        this.courseIds = List.copyOf(Objects.requireNonNull(courseIds, "courseIds cannot be null"));
        this.providerPreferenceId = providerPreferenceId;
        this.checkoutUrl = checkoutUrl;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.cancellation = cancellation;
    }

    /** Escenario 1: created together with its {@code Payment}, before any provider call. */
    public static Subscription pendingCheckout(
        UUID id, PaymentId paymentId, UUID userId, PlanId planId, String idempotencyKey, Instant createdAt
    ) {
        return new Subscription(
            id, paymentId, userId, planId, idempotencyKey, SubscriptionStatus.PENDING,
            FulfillmentStatus.PENDING_FULFILLMENT, null, null, List.of(), null, null, createdAt, null
        );
    }

    /** Records the provider preference the buyer was redirected to — never the payment id, which does not exist yet. */
    public Subscription withCheckout(String newProviderPreferenceId, String newCheckoutUrl) {
        return copy(
            status, fulfillmentStatus, startDate, endDate, courseIds, newProviderPreferenceId, newCheckoutUrl,
            cancellation
        );
    }

    /**
     * Escenario 2: the payment settled, so the subscription starts running.
     * Idempotent — replaying a webhook against an already-activated
     * subscription must not move {@code startDate} or re-snapshot the plan.
     *
     * @param confirmedAt the provider-confirmed settlement instant
     * @param durationDays {@code Plan.durationDays} at activation time
     * @param planCourseIds the plan's courses at activation time — frozen from here on
     */
    public Subscription activate(Instant confirmedAt, int durationDays, List<String> planCourseIds) {
        if (status != SubscriptionStatus.PENDING) {
            return this;
        }
        Objects.requireNonNull(confirmedAt, "confirmedAt cannot be null");
        if (durationDays <= 0) {
            throw new IllegalArgumentException("durationDays must be positive");
        }
        return copy(
            SubscriptionStatus.ACTIVE, fulfillmentStatus, confirmedAt,
            confirmedAt.plus(durationDays, ChronoUnit.DAYS), planCourseIds, providerPreferenceId, checkoutUrl,
            cancellation
        );
    }

    /**
     * Escenario 6: the payment reached a terminal non-settled state, so the
     * subscription never activates and the user's slot is released.
     * Terminal states are left untouched (monotonic, like {@code Payment}).
     */
    public Subscription cancelled() {
        if (!status.occupiesUserSlot()) {
            return this;
        }
        return copy(
            SubscriptionStatus.CANCELLED, fulfillmentStatus, startDate, endDate, courseIds,
            providerPreferenceId, checkoutUrl, cancellation
        );
    }

    /**
     * A user-initiated cancellation of the subscription's auto-renewal (US-BILLING-011).
     *
     * <p>Unlike {@link #cancelled()}, this is <strong>not</strong> idempotent: cancelling a
     * subscription that is not {@code ACTIVE} is a caller bug, unreachable over HTTP since
     * both the self-service and admin lookups filter to {@code ACTIVE} before calling this.
     * {@code endDate} is left untouched — the student keeps access until the term they
     * already paid for ends.</p>
     *
     * @param by the acting user id — the subscription's owner for self-service cancellation,
     *     an admin otherwise
     * @param reason mandatory only when {@code by} is not the owner (admin override, D1);
     *     never exposed back to the student (D2)
     * @param at the cancellation instant
     * @throws IllegalStateException if this subscription is not {@code ACTIVE}
     * @throws IllegalArgumentException if {@code by} is not the owner and {@code reason} is
     *     blank or absent
     */
    public Subscription cancel(UUID by, String reason, Instant at) {
        if (status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("cannot cancel a subscription that is not ACTIVE");
        }
        Objects.requireNonNull(by, "by cannot be null");
        Objects.requireNonNull(at, "at cannot be null");
        if (!by.equals(userId) && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("reason cannot be blank when cancelling on behalf of another user");
        }
        return copy(
            SubscriptionStatus.CANCELLED, fulfillmentStatus, startDate, endDate, courseIds,
            providerPreferenceId, checkoutUrl, new Cancellation(at, by, reason)
        );
    }

    /** Marks the locally stored entitlement snapshot as available to Virtual. */
    public Subscription assigned() {
        return copy(
            status, FulfillmentStatus.ASSIGNED, startDate, endDate, courseIds, providerPreferenceId, checkoutUrl,
            cancellation
        );
    }

    /** Snapshot activation failed; the settled payment remains recoverable on a later retry. */
    public Subscription exception() {
        return copy(
            status, FulfillmentStatus.EXCEPTION, startDate, endDate, courseIds, providerPreferenceId, checkoutUrl,
            cancellation
        );
    }

    public boolean grantsAccess() {
        return status == SubscriptionStatus.ACTIVE && fulfillmentStatus == FulfillmentStatus.ASSIGNED;
    }

    public boolean isActivated() {
        return status == SubscriptionStatus.ACTIVE;
    }

    /** True while this subscription blocks the user from starting another checkout (escenario 3). */
    public boolean occupiesUserSlot() {
        return status.occupiesUserSlot();
    }

    private Subscription copy(
        SubscriptionStatus newStatus, FulfillmentStatus newFulfillmentStatus, Instant newStartDate,
        Instant newEndDate, List<String> newCourseIds, String newProviderPreferenceId, String newCheckoutUrl,
        Cancellation newCancellation
    ) {
        return new Subscription(
            id, paymentId, userId, planId, idempotencyKey, newStatus, newFulfillmentStatus, newStartDate,
            newEndDate, newCourseIds, newProviderPreferenceId, newCheckoutUrl, createdAt, newCancellation
        );
    }

    public UUID getId() {
        return id;
    }

    public PaymentId getPaymentId() {
        return paymentId;
    }

    public UUID getUserId() {
        return userId;
    }

    public PlanId getPlanId() {
        return planId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public FulfillmentStatus getFulfillmentStatus() {
        return fulfillmentStatus;
    }

    public Optional<Instant> getStartDate() {
        return Optional.ofNullable(startDate);
    }

    public Optional<Instant> getEndDate() {
        return Optional.ofNullable(endDate);
    }

    /** The courses this subscription bought, frozen at activation. Empty while {@code PENDING}. */
    public List<String> getCourseIds() {
        return courseIds;
    }

    public Optional<String> getProviderPreferenceId() {
        return Optional.ofNullable(providerPreferenceId);
    }

    public Optional<String> getCheckoutUrl() {
        return Optional.ofNullable(checkoutUrl);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** The cancellation audit trail — present only once {@link #cancel} has run (US-BILLING-011). */
    public Optional<Cancellation> getCancellation() {
        return Optional.ofNullable(cancellation);
    }
}
