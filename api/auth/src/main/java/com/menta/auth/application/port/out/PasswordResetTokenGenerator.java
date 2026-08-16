package com.menta.auth.application.port.out;

/**
 * Cross-module port to generate opaque password-reset token material — at
 * least 256 bits of entropy, URL-safe encoding, mirroring
 * {@link ActivationTokenGenerator}. The raw value returned here MUST only be
 * handed to the delivery channel and the hasher; it is never persisted.
 */
public interface PasswordResetTokenGenerator {

    /** @return a fresh, URL-safe, high-entropy raw reset token. */
    String generate();
}
