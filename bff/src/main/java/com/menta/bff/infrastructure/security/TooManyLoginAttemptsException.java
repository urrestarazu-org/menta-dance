package com.menta.bff.infrastructure.security;

import java.time.Duration;
import org.springframework.security.core.AuthenticationException;

/**
 * Raised by the login when the Auth API throttled the attempts.
 *
 * <p>Carries the wait announced by the API so the login page can tell the user when to retry.</p>
 */
public class TooManyLoginAttemptsException extends AuthenticationException {

    private final Duration retryAfter;

    /**
     * Creates the exception.
     *
     * @param message    Description of the failure
     * @param retryAfter Wait announced by the Auth API, or null when it announced none
     */
    public TooManyLoginAttemptsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    /**
     * Returns the wait announced by the Auth API.
     *
     * @return the wait, or null when none was announced
     */
    public Duration retryAfter() {
        return retryAfter;
    }
}
