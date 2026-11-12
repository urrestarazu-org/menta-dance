package com.menta.bff.application.port.out;

/**
 * Port for reading identity claims carried by an Auth API access token.
 *
 * <p>The BFF does not authorize API calls with these claims (the API verifies
 * every token itself); it only needs them to shape the web session, e.g. the
 * authorities of the logged-in principal.</p>
 */
public interface AccessTokenClaimsReader {

    /**
     * Returns the role name carried by the token (e.g. {@code STUDENT}).
     *
     * @param accessToken compact JWT issued by the Auth API
     * @return role name, never blank
     * @throws UnreadableTokenException if the token is malformed or carries no valid role
     */
    String roleOf(String accessToken);

    /**
     * Returns the subject carried by the token: the identifier of the account it was issued to. It only tells
     * the web layer whether an account is the signed-in one; it never authorizes anything.
     *
     * @param accessToken compact JWT issued by the Auth API
     * @return account identifier, never blank
     * @throws UnreadableTokenException if the token is malformed or carries no subject
     */
    String subjectOf(String accessToken);

    /**
     * Exception thrown when the access token cannot yield the claim asked for.
     */
    class UnreadableTokenException extends RuntimeException {
        public UnreadableTokenException(String message) {
            super(message);
        }

        public UnreadableTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
