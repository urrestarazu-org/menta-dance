package com.menta.physical.application.port.out;

import java.time.Instant;

/**
 * Single-method application-layer clock port. Mirrors
 * {@code com.menta.virtual.application.port.out.Clock} so the two
 * modules can stay isolated without duplicating the bean
 * ({@code java.time.Clock#systemUTC()}) twice — the configuration in
 * each module's {@code *Configuration} wires its own
 * {@link com.menta.physical.application.port.out.Clock} bean from
 * the JDK's UTC clock.
 *
 * <p>The default production wiring returns the JDK's UTC clock so
 * use cases are timezone- and DST-independent. Tests inject a fixed
 * {@link Instant} via a stub to make time-based validations
 * deterministic.</p>
 */
public interface Clock {

    Instant now();
}
