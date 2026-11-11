package com.menta.auth.application.usecase;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.port.out.ActivationDeliveryCipher;
import com.menta.auth.application.port.out.ActivationTokenGenerator;
import com.menta.auth.application.port.out.ActivationTokenHasher;
import com.menta.auth.application.port.out.ActivationTokenRepository;
import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.domain.model.ActivationToken;
import com.menta.auth.domain.model.User;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;

/**
 * Issues an activation token for a pending account and queues the email that carries it: generate, hash, encrypt
 * the delivery envelope, persist and append the outbox event. It is the one place this sequence lives, shared by
 * registration and resend.
 *
 * <p>Nothing here is atomic on its own: the caller MUST run inside a transaction, so the token and the outbox
 * event commit together with whatever else the caller writes. The outbox event carries only the token id, never
 * the raw token.
 */
@RequiredArgsConstructor
public class ActivationTokenIssuer {

    private final ActivationTokenRepository activationTokenRepository;
    private final ActivationTokenGenerator activationTokenGenerator;
    private final ActivationTokenHasher activationTokenHasher;
    private final ActivationDeliveryCipher activationDeliveryCipher;
    private final OutboxAppender outboxAppender;
    /** TTL for a freshly-issued activation token (design.md decision #3; injected, default 24h). */
    private final Duration activationTokenTtl;

    /**
     * Issues a fresh token for the user and queues its activation email.
     *
     * @param user the pending account to activate
     * @param now the instant the token is issued at, as decided by the caller
     */
    public void issueFor(User user, Instant now) {
        String rawActivationToken = activationTokenGenerator.generate();
        String tokenHash = activationTokenHasher.hash(rawActivationToken);
        ActivationToken activationToken = ActivationToken.issue(
            user.getId(), tokenHash, now.plus(activationTokenTtl), now
        );

        DeliveryEnvelope deliveryEnvelope = activationDeliveryCipher.encrypt(
            user.getEmail().getValue() + "|" + rawActivationToken
        );
        ActivationToken savedToken = activationTokenRepository.save(activationToken, deliveryEnvelope);

        outboxAppender.append(
            AuthOutboxEventTypes.ACCOUNT_ACTIVATION_REQUESTED,
            savedToken.getId().toString(),
            "{\"activationTokenId\":\"" + savedToken.getId() + "\"}"
        );
    }
}
