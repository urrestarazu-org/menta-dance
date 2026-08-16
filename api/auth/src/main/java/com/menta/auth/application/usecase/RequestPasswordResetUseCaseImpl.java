package com.menta.auth.application.usecase;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.dto.RequestPasswordResetCommand;
import com.menta.auth.application.dto.RequestPasswordResetResult;
import com.menta.auth.application.port.in.RequestPasswordResetUseCase;
import com.menta.auth.application.port.out.Clock;
import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.application.port.out.PasswordResetDeliveryCipher;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetTokenGenerator;
import com.menta.auth.application.port.out.PasswordResetTokenHasher;
import com.menta.auth.application.port.out.PasswordResetTokenRepository;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.domain.exception.PasswordResetRateLimitedException;
import com.menta.auth.domain.model.PasswordResetToken;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Implementation of RequestPasswordResetUseCase (US-AUTH-005).
 *
 * <p>Returns the exact same {@link RequestPasswordResetResult} for a
 * nonexistent email and for every non-{@code ACTIVE} status alike — never
 * only the two the issue named (LOCKED, INACTIVE). A pending-activation
 * account has not yet proven email ownership and a suspended one is
 * administratively frozen; letting either bootstrap a password reset would
 * be its own hole, so the rule is "ACTIVE only", not an enumerated allowlist
 * of statuses to exclude. Only an ACTIVE account causes a persistence
 * side-effect: prior active tokens are invalidated and a fresh one is issued,
 * mirroring {@code RegisterUserUseCaseImpl}'s generate/hash/encrypt/persist/
 * outbox sequence. Atomicity is NOT provided here: the caller MUST wrap this
 * use case with a transactional decorator.</p>
 */
public class RequestPasswordResetUseCaseImpl implements RequestPasswordResetUseCase {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetTokenGenerator passwordResetTokenGenerator;
    private final PasswordResetTokenHasher passwordResetTokenHasher;
    private final PasswordResetDeliveryCipher passwordResetDeliveryCipher;
    private final PasswordResetRequestRateLimitPort rateLimitPort;
    private final OutboxAppender outboxAppender;
    private final Clock clock;
    /** TTL for a freshly-issued reset token (US-AUTH-005/006: 1 hour). */
    private final Duration resetTokenTtl;

    public RequestPasswordResetUseCaseImpl(
        UserRepository userRepository,
        PasswordResetTokenRepository passwordResetTokenRepository,
        PasswordResetTokenGenerator passwordResetTokenGenerator,
        PasswordResetTokenHasher passwordResetTokenHasher,
        PasswordResetDeliveryCipher passwordResetDeliveryCipher,
        PasswordResetRequestRateLimitPort rateLimitPort,
        OutboxAppender outboxAppender,
        Clock clock,
        Duration resetTokenTtl
    ) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordResetTokenGenerator = passwordResetTokenGenerator;
        this.passwordResetTokenHasher = passwordResetTokenHasher;
        this.passwordResetDeliveryCipher = passwordResetDeliveryCipher;
        this.rateLimitPort = rateLimitPort;
        this.outboxAppender = outboxAppender;
        this.clock = clock;
        this.resetTokenTtl = resetTokenTtl;
    }

    @Override
    public RequestPasswordResetResult request(RequestPasswordResetCommand command) {
        Email email = Email.of(command.email());

        RateLimitDecision decision = rateLimitPort.consume(
            emailFingerprint(email), command.clientFingerprint()
        );
        if (!decision.isAllowed()) {
            throw new PasswordResetRateLimitedException(decision.getRetryAfter());
        }

        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isPresent() && maybeUser.get().getStatus() == UserStatus.ACTIVE) {
            issueResetToken(maybeUser.get());
        }

        // Uniform outcome: identical for nonexistent and any non-ACTIVE
        // account — the caller cannot enumerate accounts or their status
        // from this return value.
        return RequestPasswordResetResult.ACKNOWLEDGED;
    }

    private void issueResetToken(User user) {
        Instant now = clock.now();
        passwordResetTokenRepository.invalidateActiveByUserId(user.getId(), now);

        String rawToken = passwordResetTokenGenerator.generate();
        String tokenHash = passwordResetTokenHasher.hash(rawToken);
        PasswordResetToken token = PasswordResetToken.issue(
            user.getId(), tokenHash, now.plus(resetTokenTtl), now
        );

        DeliveryEnvelope deliveryEnvelope = passwordResetDeliveryCipher.encrypt(
            user.getEmail().getValue() + "|" + rawToken
        );
        PasswordResetToken savedToken = passwordResetTokenRepository.save(token, deliveryEnvelope);

        outboxAppender.append(
            AuthOutboxEventTypes.PASSWORD_RESET_REQUESTED,
            savedToken.getId().toString(),
            "{\"passwordResetTokenId\":\"" + savedToken.getId() + "\"}"
        );
    }

    /** Non-reversible SHA-256 fingerprint of the email (mirrors {@code RegisterUserUseCaseImpl}). */
    private String emailFingerprint(Email email) {
        return com.menta.auth.domain.crypto.Sha256Hex.hash(email.getValue());
    }
}
