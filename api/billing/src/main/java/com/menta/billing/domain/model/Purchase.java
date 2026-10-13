package com.menta.billing.domain.model;

import com.menta.billing.domain.exception.IllegalPurchaseStateTransitionException;
import com.menta.billing.domain.exception.PurchaseResolutionConflictException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Physical-class capacity fulfillment for a {@link Payment} (US-BILLING-002,
 * #41 US-PHYSICAL-004). Created once the payment reaches {@link
 * PaymentStatus.Completed}; never before — the payment itself is the
 * financial settlement and does not wait on fulfillment succeeding.
 *
 * <p>Covers an ordered, non-empty set of physical sessions — one for an
 * {@code INDIVIDUAL} quote, N for a {@code MONTHLY} one (design A1). There is
 * no per-session status: all-or-nothing assignment (design A3) makes {@link
 * #status} complete information about every session in the list.</p>
 *
 * <p>{@code EXCEPTION} is left only by an admin {@link #resolve}, which moves to the terminal
 * {@code RESOLVED} status carrying the {@link PurchaseResolution} record (#539). The resolution is
 * present exactly when the status is {@code RESOLVED}.</p>
 */
public final class Purchase {

    private final UUID id;
    private final PaymentId paymentId;
    private final List<String> physicalSessionIds;
    private final FulfillmentStatus status;
    private final PurchaseResolution resolution;

    public Purchase(UUID id, PaymentId paymentId, List<String> physicalSessionIds, FulfillmentStatus status) {
        this(id, paymentId, physicalSessionIds, status, null);
    }

    /** {@code resolution} must be non-null exactly when {@code status} is {@code RESOLVED}. */
    public Purchase(
        UUID id, PaymentId paymentId, List<String> physicalSessionIds, FulfillmentStatus status,
        PurchaseResolution resolution
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId cannot be null");
        Objects.requireNonNull(physicalSessionIds, "physicalSessionIds cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        boolean mayHaveNoSessions = status == FulfillmentStatus.EXCEPTION || status == FulfillmentStatus.RESOLVED;
        if (physicalSessionIds.isEmpty() && !mayHaveNoSessions) {
            throw new IllegalArgumentException("physicalSessionIds cannot be empty");
        }
        if ((status == FulfillmentStatus.RESOLVED) != (resolution != null)) {
            throw new IllegalArgumentException("resolution must be present exactly when status is RESOLVED");
        }
        this.physicalSessionIds = List.copyOf(physicalSessionIds);
        this.status = status;
        this.resolution = resolution;
    }

    /**
     * Creates a purchase waiting for fulfillment, with a fresh id.
     *
     * <p>The session ids must not be empty and are kept as an immutable copy in the given order.
     */
    public static Purchase pendingFulfillment(PaymentId paymentId, List<String> physicalSessionIds) {
        return new Purchase(UUID.randomUUID(), paymentId, physicalSessionIds, FulfillmentStatus.PENDING_FULFILLMENT);
    }

    /**
     * Builds a {@code Purchase} directly at {@link FulfillmentStatus#EXCEPTION}
     * (#238), never passing through {@code PENDING_FULFILLMENT} first. This is
     * the correct shape for a payment that never resolved to any schedulable
     * session in the first place (missing payment/target, unresolvable quote,
     * coverage shortfall) — there is no legitimate intermediate pending state
     * to record, so {@code physicalSessionIds} is typically empty here (legal
     * only for {@code EXCEPTION} per the constructor's relaxed invariant).
     */
    public static Purchase exception(PaymentId paymentId, List<String> physicalSessionIds) {
        return new Purchase(UUID.randomUUID(), paymentId, physicalSessionIds, FulfillmentStatus.EXCEPTION);
    }

    /**
     * Returns a copy in {@code EXCEPTION} status that keeps the id, the payment and the sessions.
     *
     * <p>The transition is not guarded here; the use case that applies it decides which current
     * statuses may move.
     */
    public Purchase exception() {
        return new Purchase(id, paymentId, physicalSessionIds, FulfillmentStatus.EXCEPTION);
    }

    /**
     * Returns a copy in {@code ASSIGNED} status that keeps the id, the payment and the sessions.
     *
     * <p>The transition is not guarded here; the use case that applies it decides which current
     * statuses may move.
     */
    public Purchase assigned() {
        return new Purchase(id, paymentId, physicalSessionIds, FulfillmentStatus.ASSIGNED);
    }

    /**
     * Closes an {@code EXCEPTION} purchase as an audited admin decision.
     *
     * <p>Repeating the identical resolution and note on an already resolved purchase returns {@code
     * this} (idempotent). A different resolution or note on a resolved purchase fails with {@link
     * PurchaseResolutionConflictException}; any status other than {@code EXCEPTION} fails with
     * {@link IllegalPurchaseStateTransitionException}. An invalid note fails with {@link
     * IllegalArgumentException} (see {@link PurchaseResolution}).
     */
    public Purchase resolve(PurchaseResolutionType type, String note, UUID adminId, Instant now) {
        PurchaseResolution requested = new PurchaseResolution(type, note, adminId, now);
        if (isResolvedAs(requested.type(), requested.note())) {
            return this;
        }
        if (status == FulfillmentStatus.RESOLVED) {
            throw new PurchaseResolutionConflictException(id, resolution.type());
        }
        if (status != FulfillmentStatus.EXCEPTION) {
            throw new IllegalPurchaseStateTransitionException(paymentId, status, FulfillmentStatus.RESOLVED);
        }
        return new Purchase(id, paymentId, physicalSessionIds, FulfillmentStatus.RESOLVED, requested);
    }

    /** Whether this purchase is already {@code RESOLVED} with exactly this resolution and (stripped) note. */
    public boolean isResolvedAs(PurchaseResolutionType type, String note) {
        return resolution != null && resolution.type() == type && resolution.note().equals(note.strip());
    }

    /** No asistencia se habilita hasta este punto (docs/06-BILLING-API.md) — Physical's own responsibility. */
    public boolean grantsAttendance() {
        return status == FulfillmentStatus.ASSIGNED;
    }

    public UUID getId() {
        return id;
    }

    public PaymentId getPaymentId() {
        return paymentId;
    }

    public List<String> getPhysicalSessionIds() {
        return physicalSessionIds;
    }

    public FulfillmentStatus getStatus() {
        return status;
    }

    /** The admin resolution record; present only when {@link #getStatus()} is {@code RESOLVED}. */
    public Optional<PurchaseResolution> getResolution() {
        return Optional.ofNullable(resolution);
    }
}
