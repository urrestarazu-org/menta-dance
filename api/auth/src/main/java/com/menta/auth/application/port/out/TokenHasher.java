package com.menta.auth.application.port.out;

import java.util.UUID;

/**
 * Cross-module port to hash opaque refresh tokens (ADR-0025).
 *
 * <p>MySQL stores the SHA-256 hex digest of the UUID refresh so the raw token
 * never lands in storage. Infrastructure adapter chooses the concrete
 * algorithm (SHA-256 plain or HMAC-SHA256 with a separate secret — pending
 * Open Question in design.md).
 */
public interface TokenHasher {

    /**
     * Hashes a raw refresh token into the digest that is persisted and looked up.
     *
     * @param rawRefreshToken UUID string as presented by the client.
     * @return 64-char lowercase hex digest suitable for column token_hash.
     */
    String hash(String rawRefreshToken);

    /**
     * Convenience overload for typed inputs.
     */
    default String hash(UUID refreshId) {
        if (refreshId == null) {
            throw new IllegalArgumentException("refreshId cannot be null");
        }
        return hash(refreshId.toString());
    }
}
