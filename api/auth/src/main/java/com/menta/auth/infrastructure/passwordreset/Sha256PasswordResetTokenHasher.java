package com.menta.auth.infrastructure.passwordreset;

import com.menta.auth.application.port.out.PasswordResetTokenHasher;
import com.menta.auth.domain.crypto.Sha256Hex;

/** Computes the SHA-256 digest persisted for an opaque password-reset credential. */
public final class Sha256PasswordResetTokenHasher implements PasswordResetTokenHasher {

    @Override
    public String hash(String rawToken) {
        if (rawToken == null) {
            throw new IllegalArgumentException("rawToken cannot be null");
        }
        return Sha256Hex.hash(rawToken);
    }
}
