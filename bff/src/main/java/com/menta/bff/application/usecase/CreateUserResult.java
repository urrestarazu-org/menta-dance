package com.menta.bff.application.usecase;

/**
 * What came of an administrator's attempt to create a staff account. A failed attempt is a result, not an
 * exception, so the screen can answer each outcome in its own words.
 *
 * @param outcome     how the attempt ended
 * @param displayName first and last name of the new account's owner, only for {@link Outcome#CREATED}
 */
public record CreateUserResult(Outcome outcome, String displayName) {

    /** How the attempt ended. */
    public enum Outcome {
        /** The account exists and its owner was sent the activation link. */
        CREATED,
        /** An account with that email already exists. */
        EMAIL_ALREADY_REGISTERED,
        /** The data was refused, before or by the API. */
        INVALID,
        /** The API could not be reached or answered something unusable. */
        UNAVAILABLE
    }

    static CreateUserResult created(String displayName) {
        return new CreateUserResult(Outcome.CREATED, displayName);
    }

    static CreateUserResult of(Outcome outcome) {
        return new CreateUserResult(outcome, null);
    }
}
