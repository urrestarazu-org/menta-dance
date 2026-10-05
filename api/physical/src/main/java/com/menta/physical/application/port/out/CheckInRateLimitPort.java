package com.menta.physical.application.port.out;

import com.menta.physical.application.dto.RateLimitDecision;

/**
 * Throttles the QR check-in before the reader is authenticated (ADR-0044). Two independent
 * budgets: one per client IP and one per device id. Both arguments are opaque, bounded
 * fingerprints derived by the web adapter, never raw request values.
 *
 * <p>Implementations must fail closed: if the budget cannot be evaluated they throw {@link
 * com.menta.physical.domain.exception.CheckInDegradedException} instead of allowing the request.</p>
 */
public interface CheckInRateLimitPort {

    /** Consumes one unit from each budget and reports whether the request may proceed. */
    RateLimitDecision consume(String clientFingerprint, String deviceFingerprint);
}
