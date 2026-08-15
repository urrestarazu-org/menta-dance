package com.menta.auth.application.port.out;

import com.menta.auth.domain.model.ActivationToken;
import com.menta.auth.domain.model.UserId;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence port for the {@link ActivationToken} aggregate.
 *
 * All operations MUST participate in the caller's transaction so the token
 * lifecycle shares an atomic COMMIT with the originating user/outbox
 * mutation (auth-account-activation spec: "Registro pendiente y entrega
 * durable").
 */
public interface ActivationTokenRepository {

    ActivationToken save(ActivationToken token);

    /**
     * Persists the token together with its encrypted delivery envelope in
     * the same row (V3's {@code delivery_ciphertext}/{@code delivery_nonce}/
     * {@code delivery_key_version} columns), keeping registration a single
     * atomic write (auth-account-activation spec: "Registro pendiente y
     * entrega durable"). The {@link com.menta.auth.domain.model.ActivationToken}
     * aggregate cannot carry the envelope itself without pulling an
     * application-layer type into domain, so the repository accepts it
     * alongside the token instead.
     *
     * <p>Implementers MUST persist {@code deliveryEnvelope} durably alongside
     * the token in the same write. There is no default implementation:
     * silently discarding the envelope would compile but never deliver the
     * activation email, a data-loss failure mode this port must make
     * impossible to ship by accident.</p>
     */
    ActivationToken save(ActivationToken token, DeliveryEnvelope deliveryEnvelope);

    Optional<ActivationToken> findByHash(String tokenHash);

    /**
     * Invalidates every currently-active token for a user (used before
     * issuing a fresh one on resend). Idempotent — invalidating twice has
     * the same effect as invalidating once.
     */
    void invalidateActiveByUserId(UserId userId, Instant now);
}
