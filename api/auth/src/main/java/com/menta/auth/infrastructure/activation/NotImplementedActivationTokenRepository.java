package com.menta.auth.infrastructure.activation;

import com.menta.auth.application.port.out.ActivationTokenRepository;
import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.domain.model.ActivationToken;
import com.menta.auth.domain.model.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * Compile-boundary placeholder for {@link ActivationTokenRepository}.
 *
 * <p>PR1 wires the atomic register use case against this contract with a
 * fully mocked test suite; no real persistence adapter exists yet. Every
 * method fails loudly if ever invoked at runtime so a missing PR2 rollout is
 * never silently swallowed.</p>
 *
 * // TODO(PR2 task 2.1): replace with real JPA adapter.
 */
public class NotImplementedActivationTokenRepository implements ActivationTokenRepository {

    private static final String MESSAGE =
        "ActivationTokenRepository JPA adapter not implemented yet — see task 2.1";

    @Override
    public ActivationToken save(ActivationToken token) {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public ActivationToken save(ActivationToken token, DeliveryEnvelope deliveryEnvelope) {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public Optional<ActivationToken> findByHash(String tokenHash) {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public void invalidateActiveByUserId(UserId userId, Instant now) {
        throw new UnsupportedOperationException(MESSAGE);
    }
}
