package com.menta.auth.infrastructure.activation;

import com.menta.auth.application.port.out.ActivationTokenHasher;

/**
 * Compile-boundary placeholder for {@link ActivationTokenHasher}.
 *
 * // TODO(PR2 task 2.2): replace with a SHA-256 hasher adapter.
 */
public class NotImplementedActivationTokenHasher implements ActivationTokenHasher {

    private static final String MESSAGE =
        "ActivationTokenHasher adapter not implemented yet — see task 2.2";

    @Override
    public String hash(String rawToken) {
        throw new UnsupportedOperationException(MESSAGE);
    }
}
