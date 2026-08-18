package com.menta.billing.application.port.out;

import java.time.Instant;

/**
 * Application-layer clock abstraction — mirrors {@code auth}'s identical
 * port. Use cases must depend on this instead of calling {@code
 * Instant.now()} directly, so tests stay deterministic.
 */
public interface Clock {

    Instant now();
}
