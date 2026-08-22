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
 *   <li>{@link FulfillmentStatus} — whether Virtual actually granted access,
 *       the same axis {@link Purchase} uses. US-BILLING-010's integrity NFR
 *       requires an {@code ACTIVE} subscription whose grant failed to be
 *       representable ("un Payment puede quedar COMPLETED aunque el
 *       otorgamiento de acceso falle"), so collapsing the two would erase a
 *       state the business explicitly asks for.</li>
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

    public Subscription(
        UUID id, PaymentId paymentId, UUID userId, PlanId planId, String idempotencyKey,
        SubscriptionStatus status, FulfillmentStatus fulfillmentStatus, Instant startDate, Instant endDate,
        List<String> courseIds, String providerPreferenceId, String checkoutUrl, Instant createdAt
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
    }

    /** Escenario 1: created together with its {@code Payment}, before any provider call. */
    public static Subscription pendingCheckout(
        UUID id, PaymentId paymentId, UUID userId, PlanId planId, String idempotencyKey, Instant createdAt
    ) {
        return new Subscription(
            id, paymentId, userId, planId, idempotencyKey, SubscriptionStatus.PENDING,
            FulfillmentStatus.PENDING_FULFILLMENT, null, null, List.of(), null, null, createdAt
        );
    }

    /** Records the provider preference the buyer was redirected to — never the payment id, which does not exist yet. */
    public Subscription withCheckout(String newProviderPreferenceId, String newCheckoutUrl) {
        return copy(status, fulfillmentStatus, startDate, endDate, courseIds, newProviderPreferenceId, newCheckoutUrl);
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
            confirmedAt.plus(durationDays, ChronoUnit.DAYS), planCourseIds, providerPreferenceId, checkoutUrl
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
            providerPreferenceId, checkoutUrl
        );
    }

    /** Virtual granted access to every course in the snapshot. */
    public Subscription assigned() {
        return copy(
            status, FulfillmentStatus.ASSIGNED, startDate, endDate, courseIds, providerPreferenceId, checkoutUrl
        );
    }

    /** The access grant failed — the payment stays settled, this is a fulfillment exception. */
    public Subscription exception() {
        return copy(
            status, FulfillmentStatus.EXCEPTION, startDate, endDate, courseIds, providerPreferenceId, checkoutUrl
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
        Instant newEndDate, List<String> newCourseIds, String newProviderPreferenceId, String newCheckoutUrl
    ) {
        return new Subscription(
            id, paymentId, userId, planId, idempotencyKey, newStatus, newFulfillmentStatus, newStartDate,
            newEndDate, newCourseIds, newProviderPreferenceId, newCheckoutUrl, createdAt
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
}
