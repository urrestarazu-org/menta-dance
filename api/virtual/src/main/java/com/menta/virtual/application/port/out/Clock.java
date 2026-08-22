package com.menta.virtual.application.port.out;

import java.time.Instant;

/**
 * Application-layer clock abstraction for Virtual use cases.
 * Mirrors the same single-method port in {@code auth} and {@code billing}
 * so use cases can be tested deterministically without mocking
 * {@link java.time.Clock} directly.
 *
 * <p>The bean in
 * {@link com.menta.virtual.infrastructure.config.VirtualConfiguration}
 * wires the JDK's {@link java.time.Clock#systemUTC()} as the production
 * implementation.</p>
 */
public interface Clock {

    Instant now();
}
