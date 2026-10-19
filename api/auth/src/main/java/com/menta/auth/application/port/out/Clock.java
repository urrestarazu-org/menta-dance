package com.menta.auth.application.port.out;

import java.time.Instant;

/**
 * Application-layer clock abstraction.
 *
 * <p>Use cases MUST depend on this port instead of calling
 * {@code Instant.now()} directly, so unit tests can inject a fixed instant
 * and stay deterministic (auth-account-activation tasks.md 1.7). Production
 * wiring binds this to a system-clock adapter; tests stub it with Mockito.</p>
 */
public interface Clock {

    /**
     * Returns the current instant according to this clock.
     *
     * @return the instant to use as "now" for expiry and timestamp decisions within a single use case
     */
    Instant now();
}
