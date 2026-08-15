package com.menta.auth.infrastructure.security;

import com.menta.auth.application.port.out.TokenHasher;
import com.menta.auth.domain.crypto.Sha256Hex;

/**
 * SHA-256 hex digest adapter for the TokenHasher port (ADR-0025).
 *
 * MySQL stores the lowercase hex digest of the raw refresh UUID; the raw
 * token never lands in storage. The algorithm is FIPS-compatible — no
 * per-tenant secret so far (open question in design.md tracks the eventual
 * HMAC variant if we add a per-tenant secret).
 *
 * Implementation notes:
 *   - Algorithm availability is verified when the adapter is constructed because SHA-256 is a
 *     mandatory JRE algorithm. We translate NoSuchAlgorithmException to
 *     IllegalStateException at construction time so a broken JVM cannot
 *     silently produce wrong data.
 *   - The digest is rebuilt per call — there is no shared mutable state.
 *     {@link java.security.MessageDigest} is NOT thread-safe; do not cache a single instance.
 */
public class Sha256TokenHasher implements TokenHasher {

    public Sha256TokenHasher() {
        Sha256Hex.verifyAlgorithmAvailable();
    }

    @Override
    public String hash(String rawRefreshToken) {
        if (rawRefreshToken == null) {
            throw new IllegalArgumentException("rawRefreshToken cannot be null");
        }
        return Sha256Hex.hash(rawRefreshToken);
    }
}
