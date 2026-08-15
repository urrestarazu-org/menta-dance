package com.menta.auth.infrastructure.activation;

import com.menta.auth.application.port.out.ActivationTokenGenerator;

/**
 * Compile-boundary placeholder for {@link ActivationTokenGenerator}.
 *
 * // TODO(PR2 task 2.2): replace with SecureRandomActivationTokenGenerator.
 */
public class NotImplementedActivationTokenGenerator implements ActivationTokenGenerator {

    private static final String MESSAGE =
        "ActivationTokenGenerator adapter not implemented yet — see task 2.2";

    @Override
    public String generate() {
        throw new UnsupportedOperationException(MESSAGE);
    }
}
