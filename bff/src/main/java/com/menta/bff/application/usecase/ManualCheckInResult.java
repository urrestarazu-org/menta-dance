package com.menta.bff.application.usecase;

/**
 * What came of the attempt to register a student's attendance by hand. A failed attempt is a result, not an
 * exception, so the screen can answer each outcome in its own words.
 *
 * @param outcome         how the attempt ended
 * @param recordedAtLabel Spanish date and time the attendance was recorded; only for the registered outcomes
 * @param kindLabel       how it was recorded ({@code QR} or {@code manual}); only for the registered outcomes
 */
public record ManualCheckInResult(Outcome outcome, String recordedAtLabel, String kindLabel) {

    /** How the attempt ended. */
    public enum Outcome {
        /** The attendance was recorded now. */
        REGISTERED,
        /** The attendance already existed: the API answered the idempotent replay, nothing was duplicated. */
        ALREADY_REGISTERED,
        /** The student has no confirmed spot in the session. */
        NO_SPOT,
        /** The session is outside its check-in window. */
        OUTSIDE_WINDOW,
        /** The session is cancelled. */
        SESSION_CANCELLED,
        /** The identifiers were refused before calling the API: they are not UUIDs. */
        INVALID,
        /** The API could not be reached or answered something unusable. */
        UNAVAILABLE
    }

    static ManualCheckInResult of(Outcome outcome) {
        return new ManualCheckInResult(outcome, null, null);
    }
}
