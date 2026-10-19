package com.menta.physical.application.dto;

import java.time.Instant;

/**
 * Application-layer view returned by
 * {@link com.menta.physical.application.port.in.IssuePhysicalAccessQrUseCase}.
 * Carries the placeholder {@code qrCredentials} token and a strong
 * {@code expiresAt} the door-side use case can compare against {@code now}
 * without trusting the client clock (US-PHYSICAL-001 escenario 1).
 *
 * <p>{@code refreshAfterSeconds} is a client-side hint, not part of the
 * signature: it tells the student app to re-issue the QR every 30 seconds.
 * The MVP returns a constant; a future revision might compute it from
 * {@code (expiresAt - now)} or the network round-trip budget.</p>
 */
public record AccessQrView(
    String qrCredentials,
    Instant expiresAt,
    int refreshAfterSeconds
) {
}
