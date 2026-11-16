package com.menta.bff.application.usecase;

/**
 * What came of the student's attempt to cancel their subscription. A failed attempt is a result, not an
 * exception, so the dashboard can answer each outcome in its own words.
 *
 * @param outcome        how the attempt ended
 * @param accessEndsLabel date the access lasts until as {@code dd/MM/yyyy}; only for {@link Outcome#CANCELLED}
 */
public record CancelSubscriptionResult(Outcome outcome, String accessEndsLabel) {

    /** How the attempt ended. */
    public enum Outcome {
        /** The renewal was cancelled; the access lasts until the end of the paid period. */
        CANCELLED,
        /** The student has no active subscription to cancel. */
        NOTHING_TO_CANCEL,
        /** The subscription changed while it was being cancelled; trying again resolves it. */
        CONFLICT,
        /** The request had no token, or the Billing API could not be reached or answered something unusable. */
        UNAVAILABLE
    }

    /** A result that carries no date: every outcome but {@link Outcome#CANCELLED}. */
    public static CancelSubscriptionResult of(Outcome outcome) {
        return new CancelSubscriptionResult(outcome, null);
    }
}
