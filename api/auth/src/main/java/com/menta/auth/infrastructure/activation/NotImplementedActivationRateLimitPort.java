package com.menta.auth.infrastructure.activation;

import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.RateLimitDecision;

/**
 * Compile-boundary placeholder for {@link ActivationRateLimitPort}.
 *
 * // TODO(PR2 task 2.4): replace with real Redis-backed adapter.
 */
public class NotImplementedActivationRateLimitPort implements ActivationRateLimitPort {

    private static final String MESSAGE =
        "ActivationRateLimitPort Redis adapter not implemented yet — see task 2.4";

    @Override
    public RateLimitDecision consume(String emailFingerprint, String clientFingerprint) {
        throw new UnsupportedOperationException(MESSAGE);
    }
}
