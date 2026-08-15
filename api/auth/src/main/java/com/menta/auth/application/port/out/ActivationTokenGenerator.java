package com.menta.auth.application.port.out;

/**
 * Cross-module port to generate opaque activation token material
 * (auth-account-activation spec: "Secreto de activación" — at least 256 bits
 * of entropy, URL-safe encoding). The raw value returned here MUST only be
 * handed to the delivery channel and the hasher; it is never persisted.
 */
public interface ActivationTokenGenerator {

    /**
     * @return a fresh, URL-safe, high-entropy raw activation token.
     */
    String generate();
}
