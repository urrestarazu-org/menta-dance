package com.menta.billing.domain.model;

/**
 * Codified residual reasons for the post-payment presential orchestration
 * (ADR-0028 §Decisión; design §4.2).
 *
 * <p>The {@code api:app} outbox handler routes any of these to
 * {@link com.menta.billing.application.port.in.MarkPurchaseExceptionPort};
 * the surviving {@link FulfillmentStatus#EXCEPTION} state is the
 * documented terminal residual — never to be silently remapped by a
 * compassionate retry policy in a future change.</p>
 */
public enum Reason {

    /**
     * {@link com.menta.physical.domain.exception.CapacityBelowAssignedException} — capacity was reduced below already
     * assigned spots, or a new read-time check trips. Real capacity only: a student who already holds the session
     * is {@link #ALREADY_ASSIGNED}.
     */
    CAPACITY_BELOW_ASSIGNED,

    /**
     * {@link com.menta.physical.domain.exception.StudentAlreadyAssignedException} — the student already holds a
     * confirmed spot in a session the purchase tried to assign (V7 {@code UNIQUE (session_id, student_id)}); the
     * session may have spare capacity.
     */
    ALREADY_ASSIGNED,

    /** V7 {@code UNIQUE (session_id, student_id)} collision — concurrent INSERT lost to another handler. */
    UNIQUE_COLLISION,

    /** Hold expired between checkout and confirm — purchaser's seat reservation fell off. */
    HOLD_EXPIRED,

    /** Monthly coverage changed since quote — the plan no longer covers the session. */
    COVERAGE_CHANGED,

    /** Target session is no longer {@code SCHEDULED} (cancelled, archived). */
    TARGET_NOT_SCHEDULED,

    /**
     * The purchase's quote no longer exists when the payment is confirmed, so the assignment type cannot be
     * resolved. Distinct from {@link #TARGET_NOT_SCHEDULED}: the session may be perfectly schedulable, the
     * purchase data needed to assign it is what is missing.
     */
    QUOTE_NOT_FOUND
}
