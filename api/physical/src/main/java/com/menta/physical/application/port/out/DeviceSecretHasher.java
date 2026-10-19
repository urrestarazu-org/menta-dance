package com.menta.physical.application.port.out;

/** Hashes a raw device secret into its persisted form (#44, US-PHYSICAL-007, design C2). */
public interface DeviceSecretHasher {

    /**
     * Hashes a raw device secret into the value that is persisted.
     *
     * <p>The production adapter is deterministic (lowercase SHA-256 hex digest) and rejects a
     * {@code null} secret.
     */
    String hash(String rawSecret);
}
