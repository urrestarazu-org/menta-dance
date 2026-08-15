package com.menta.auth.application.usecase;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.dto.ResendActivationCommand;
import com.menta.auth.application.dto.ResendActivationResult;
import com.menta.auth.application.port.in.ResendActivationUseCase;
import com.menta.auth.application.port.out.ActivationDeliveryCipher;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.ActivationTokenGenerator;
import com.menta.auth.application.port.out.ActivationTokenHasher;
import com.menta.auth.application.port.out.ActivationTokenRepository;
import com.menta.auth.application.port.out.Clock;
import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.domain.crypto.Sha256Hex;
import com.menta.auth.domain.exception.ActivationRateLimitedException;
import com.menta.auth.domain.model.ActivationToken;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Implementation of ResendActivationUseCase.
 *
 * <p>Returns the exact same {@link ResendActivationResult} for a nonexistent
 * email, an already-active account, and a pending account so the response
 * shape never reveals account existence or state (auth-account-activation
 * design decision #6: "Respuesta uniforme para registro/reenvío"). Only a
 * pending account causes a persistence side-effect: prior active tokens are
 * invalidated and a fresh one is issued, mirroring
 * {@code RegisterUserUseCaseImpl}'s generate/hash/encrypt/persist/outbox
 * sequence. Atomicity is NOT provided here: the caller MUST wrap this use
 * case with a transactional decorator (see
 * {@code TransactionalResendActivationUseCase}).</p>
 */
public class ResendActivationUseCaseImpl implements ResendActivationUseCase {

    private final UserRepository userRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final ActivationTokenGenerator activationTokenGenerator;
    private final ActivationTokenHasher activationTokenHasher;
    private final ActivationDeliveryCipher activationDeliveryCipher;
    private final ActivationRateLimitPort activationRateLimitPort;
    private final OutboxAppender outboxAppender;
    private final Clock clock;
    /** TTL for a freshly-issued activation token (design.md decision #3; injected, default 24h). */
    private final Duration activationTokenTtl;

    public ResendActivationUseCaseImpl(
        UserRepository userRepository,
        ActivationTokenRepository activationTokenRepository,
        ActivationTokenGenerator activationTokenGenerator,
        ActivationTokenHasher activationTokenHasher,
        ActivationDeliveryCipher activationDeliveryCipher,
        ActivationRateLimitPort activationRateLimitPort,
        OutboxAppender outboxAppender,
        Clock clock,
        Duration activationTokenTtl
    ) {
        this.userRepository = userRepository;
        this.activationTokenRepository = activationTokenRepository;
        this.activationTokenGenerator = activationTokenGenerator;
        this.activationTokenHasher = activationTokenHasher;
        this.activationDeliveryCipher = activationDeliveryCipher;
        this.activationRateLimitPort = activationRateLimitPort;
        this.outboxAppender = outboxAppender;
        this.clock = clock;
        this.activationTokenTtl = activationTokenTtl;
    }

    @Override
    public ResendActivationResult resend(ResendActivationCommand command) {
        Email email = Email.of(command.email());

        RateLimitDecision decision = activationRateLimitPort.consume(
            emailFingerprint(email), command.clientFingerprint()
        );
        if (!decision.isAllowed()) {
            throw new ActivationRateLimitedException(decision.getRetryAfter());
        }

        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isPresent() && maybeUser.get().getStatus() == UserStatus.PENDING_ACTIVATION) {
            reissueActivationToken(maybeUser.get());
        }

        // Uniform outcome: identical for nonexistent, already-active and
        // pending accounts — the caller cannot enumerate accounts from this
        // return value (design.md decision #6).
        return ResendActivationResult.ACKNOWLEDGED;
    }

    private void reissueActivationToken(User user) {
        Instant now = clock.now();
        activationTokenRepository.invalidateActiveByUserId(user.getId(), now);

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

    /** Non-reversible SHA-256 fingerprint of the email (mirrors {@code RegisterUserUseCaseImpl}). */
    private String emailFingerprint(Email email) {
        return Sha256Hex.hash(email.getValue());
    }
}
