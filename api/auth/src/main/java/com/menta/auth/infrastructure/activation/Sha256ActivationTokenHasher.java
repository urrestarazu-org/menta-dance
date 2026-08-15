package com.menta.auth.infrastructure.activation;

import com.menta.auth.application.port.out.ActivationTokenHasher;
import com.menta.auth.infrastructure.security.Sha256Hex;

/** Computes the SHA-256 digest persisted for an opaque activation credential. */
public final class Sha256ActivationTokenHasher implements ActivationTokenHasher {

    @Override
    public String hash(String rawToken) {
        if (rawToken == null) {
            throw new IllegalArgumentException("rawToken cannot be null");
        }
        return Sha256Hex.hash(rawToken);
    }
}
