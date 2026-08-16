package com.menta.auth.application.port.out;

import com.menta.auth.domain.model.PasswordResetToken;
import com.menta.auth.domain.model.UserId;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence port for the {@link PasswordResetToken} aggregate. Mirrors
 * {@link ActivationTokenRepository} — same durable-delivery shape, same
 * atomicity requirement — but for a distinct security domain: burning a
 * reset budget must never touch activation state and vice versa.
 *
 * All operations MUST participate in the caller's transaction so the token
 * lifecycle shares an atomic COMMIT with the originating outbox mutation.
 */
public interface PasswordResetTokenRepository {

    /**
     * Persists the token together with its encrypted delivery envelope in the
     * same row, keeping the request a single atomic write. There is no
     * overload that omits the envelope: silently discarding it would compile
     * but never deliver the reset email, a data-loss failure mode this port
     * makes impossible to ship by accident.
     */
    PasswordResetToken save(PasswordResetToken token, DeliveryEnvelope deliveryEnvelope);

    Optional<PasswordResetToken> findByHash(String tokenHash);

    /**
     * Atomically consumes the token only while it remains active. A false
     * result means a concurrent request consumed, invalidated, or expired it.
     */
    boolean consumeIfActive(PasswordResetToken token, Instant now);

    /** Removes ciphertext, nonce and key version after confirmed delivery. */
    void clearDeliveryEnvelope(java.util.UUID tokenId);

    /**
     * Invalidates every currently-active token for a user (US-AUTH-005
     * escenario 3: a fresh request supersedes prior ones). Idempotent.
     */
    void invalidateActiveByUserId(UserId userId, Instant now);
}
