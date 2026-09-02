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
 *
 * <p>{@link SubscriptionType} (US-BILLING-012) tells whether this row was
 * paid for or granted by an admin free of charge. The pairing with {@code
 * paymentId}/{@link TrialGrant} is a structural invariant enforced by the
 * canonical constructor (design A17): {@code PAID} always carries a payment
 * and never a grant, {@code TRIAL} always carries a grant and never a
 * payment. {@code type} is descriptive only — never an authorization input
 * (design D6).</p>
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
    private final SubscriptionType type;
    private final TrialGrant trialGrant;
    private final long version;

    /**
     * Public constructor for a freshly created subscription — never rehydrated from storage, so
     * it always starts at optimistic-lock {@code version} {@code 0} (design A14). Delegates to
     * the rehydration constructor below, the single choke point that enforces the type/payment/
     * grant invariants (design A17).
     */
    public Subscription(
        UUID id, PaymentId paymentId, UUID userId, PlanId planId, String idempotencyKey,
        SubscriptionStatus status, FulfillmentStatus fulfillmentStatus, Instant startDate, Instant endDate,
        List<String> courseIds, String providerPreferenceId, String checkoutUrl, Instant createdAt,
        Cancellation cancellation, SubscriptionType type, TrialGrant trialGrant
    ) {
        this(
            id, paymentId, userId, planId, idempotencyKey, status, fulfillmentStatus, startDate, endDate,
            courseIds, providerPreferenceId, checkoutUrl, createdAt, cancellation, type, trialGrant, 0L
        );
    }

    /**
     * Rehydration constructor — the only one that carries a persisted optimistic-lock {@code
     * version} (design A14), used by {@code SubscriptionJpaMapper.toDomain} and by {@link
     * #copy} so a transition never resets the version Hibernate needs to compare against.
     *
     * <p>Both public constructors funnel through here, which is what makes this the single
     * choke point for the type/payment/grant invariants (design A17): {@code PAID} implies a
     * payment and no grant, {@code TRIAL} implies a grant and no payment. Enforcing it only in
     * the factories would leave this constructor — already used directly by tests and the
     * mapper — unguarded, and a future {@code copy(...)} edit could mint an inconsistent pair.
     *
     * @throws IllegalArgumentException if {@code type} and the {@code paymentId}/{@code
     *     trialGrant} pairing do not match one of the two legal shapes
     */
    public Subscription(
        UUID id, PaymentId paymentId, UUID userId, PlanId planId, String idempotencyKey,
        SubscriptionStatus status, FulfillmentStatus fulfillmentStatus, Instant startDate, Instant endDate,
        List<String> courseIds, String providerPreferenceId, String checkoutUrl, Instant createdAt,
        Cancellation cancellation, SubscriptionType type, TrialGrant trialGrant, long version
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        // No requireNonNull here (design A1): a TRIAL subscription has no Payment.
        this.paymentId = paymentId;
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
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.trialGrant = trialGrant;
        this.version = version;

        if (type == SubscriptionType.PAID) {
            if (paymentId == null) {
                throw new IllegalArgumentException("a PAID subscription requires a paymentId");
            }
            if (trialGrant != null) {
                throw new IllegalArgumentException("a PAID subscription cannot carry a trialGrant");
            }
        } else {
            if (paymentId != null) {
                throw new IllegalArgumentException("a TRIAL subscription cannot carry a paymentId");
            }
            if (trialGrant == null) {
                throw new IllegalArgumentException("a TRIAL subscription requires a trialGrant");
            }
        }
    }

    /** Escenario 1: created together with its {@code Payment}, before any provider call. */
    public static Subscription pendingCheckout(
        UUID id, PaymentId paymentId, UUID userId, PlanId planId, String idempotencyKey, Instant createdAt
    ) {
        return new Subscription(
            id, paymentId, userId, planId, idempotencyKey, SubscriptionStatus.PENDING,
            FulfillmentStatus.PENDING_FULFILLMENT, null, null, List.of(), null, null, createdAt, null,
            SubscriptionType.PAID, null
        );
    }

    /**
     * Admin-assigned trial (US-BILLING-012): born {@code ACTIVE} and {@code ASSIGNED} with no
     * {@code Payment}, using the same frozen {@code courseIds} snapshot a paid subscription
     * would use. {@code endDate} is derived only from {@code days} — the admin's own decision,
     * never {@code Plan.durationDays} — and the idempotency key is server-generated (design D7)
     * since no client-supplied one exists for this admin-only flow.
     *
     * @param days the trial's duration, taken from the admin's request
     * @param grant the audit trail — actor, timestamp, reason, and the granted number of days
     */
    public static Subscription trial(
        UUID id, UUID userId, PlanId planId, Instant now, int days, List<String> courseIds, TrialGrant grant
    ) {
        Objects.requireNonNull(now, "now cannot be null");
        return new Subscription(
            id, null, userId, planId, "trial:" + id, SubscriptionStatus.ACTIVE, FulfillmentStatus.ASSIGNED,
            now, now.plus(days, ChronoUnit.DAYS), courseIds, null, null, now, null, SubscriptionType.TRIAL, grant
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

    /**
     * Automatic expiry (US-BILLING-012, design A13): {@code ACTIVE → EXPIRED}, regardless of
     * {@link SubscriptionType}. {@code endDate} is never touched — expiry only records that the
     * term already ended.
     *
     * <p>The two guards have deliberately different failure modes. A non-{@code ACTIVE} status
     * is a legitimate race (a concurrent cancellation, or a replayed sweep) and is a silent
     * no-op, same discipline as {@link #cancelled()}. A not-yet-due {@code endDate} can only
     * mean a broken caller — time only moves forward, so anything eligible now stays eligible —
     * and is loud, same discipline as {@link #cancel}.</p>
     *
     * @param at the instant expiry is evaluated at
     * @throws IllegalStateException if this subscription is {@code ACTIVE} but its {@code
     *     endDate} has not passed yet (boundary: {@code endDate <= at} expires, matching the
     *     access semantics of {@code findLatestCancelledWithRemainingAccess})
     */
    public Subscription expire(Instant at) {
        if (status != SubscriptionStatus.ACTIVE) {
            return this;
        }
        Objects.requireNonNull(at, "at cannot be null");
        if (endDate.isAfter(at)) {
            throw new IllegalStateException("cannot expire a subscription whose endDate has not passed");
        }
        return copy(
            SubscriptionStatus.EXPIRED, fulfillmentStatus, startDate, endDate, courseIds, providerPreferenceId,
            checkoutUrl, cancellation
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
            newEndDate, newCourseIds, newProviderPreferenceId, newCheckoutUrl, createdAt, newCancellation,
            type, trialGrant, version
        );
    }

    public UUID getId() {
        return id;
    }

    /** Absent for a {@code TRIAL} subscription, which has no {@code Payment} (design A1). */
    public Optional<PaymentId> getPaymentId() {
        return Optional.ofNullable(paymentId);
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

    /** Whether this subscription was paid for or granted (US-BILLING-012). Descriptive only — never an authorization input. */
    public SubscriptionType getType() {
        return type;
    }

    /** The trial's audit trail — present only when {@link #getType()} is {@code TRIAL}. */
    public Optional<TrialGrant> getTrialGrant() {
        return Optional.ofNullable(trialGrant);
    }

    /** Optimistic-lock version (design A14) — {@code 0} for a subscription that was never persisted. */
    public long getVersion() {
        return version;
    }
}
