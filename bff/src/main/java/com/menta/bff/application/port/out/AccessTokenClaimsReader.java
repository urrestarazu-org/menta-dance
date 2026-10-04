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
     * Exception thrown when the access token cannot yield a role.
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
