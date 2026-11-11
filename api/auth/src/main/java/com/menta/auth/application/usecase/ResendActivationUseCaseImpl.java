package com.menta.auth.application.usecase;

import com.menta.auth.application.dto.ResendActivationCommand;
import com.menta.auth.application.dto.ResendActivationResult;
import com.menta.auth.application.port.in.ResendActivationUseCase;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.ActivationTokenRepository;
import com.menta.auth.application.port.out.Clock;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.domain.crypto.Sha256Hex;
import com.menta.auth.domain.exception.ActivationRateLimitedException;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of ResendActivationUseCase.
 *
 * <p>Returns the exact same {@link ResendActivationResult} for a nonexistent
 * email, an already-active account, and a pending account so the response
 * shape never reveals account existence or state (auth-account-activation
 * design decision #6: "Respuesta uniforme para registro/reenvío"). Only a
 * pending account causes a persistence side-effect: prior active tokens are
 * invalidated and a fresh one is issued through {@link ActivationTokenIssuer},
 * the same sequence {@code RegisterUserUseCaseImpl} uses. Atomicity is NOT provided here: the caller MUST wrap this use
 * case with a transactional decorator (see
 * {@code TransactionalResendActivationUseCase}).</p>
 */
public class ResendActivationUseCaseImpl implements ResendActivationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResendActivationUseCaseImpl.class);

    private final UserRepository userRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final ActivationRateLimitPort activationRateLimitPort;
    private final ActivationTokenIssuer activationTokenIssuer;
    private final Clock clock;

    public ResendActivationUseCaseImpl(
        UserRepository userRepository,
        ActivationTokenRepository activationTokenRepository,
        ActivationRateLimitPort activationRateLimitPort,
        ActivationTokenIssuer activationTokenIssuer,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.activationTokenRepository = activationTokenRepository;
        this.activationRateLimitPort = activationRateLimitPort;
        this.activationTokenIssuer = activationTokenIssuer;
        this.clock = clock;
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
            if (maybeUser.get().isDemo()) {
                // Demo accounts receive no email: issue no token and enqueue nothing. The caller still gets
                // the uniform response, and the log line carries no identity data.
                log.info("Activation email skipped for a demo account");
            } else {
                reissueActivationToken(maybeUser.get());
            }
        }

        // Uniform outcome: identical for nonexistent, already-active and
        // pending accounts — the caller cannot enumerate accounts from this
        // return value (design.md decision #6).
        return ResendActivationResult.ACKNOWLEDGED;
    }

    private void reissueActivationToken(User user) {
        Instant now = clock.now();
        activationTokenRepository.invalidateActiveByUserId(user.getId(), now);
        activationTokenIssuer.issueFor(user, now);
    }

    /** Non-reversible SHA-256 fingerprint of the email (mirrors {@code RegisterUserUseCaseImpl}). */
    private String emailFingerprint(Email email) {
        return Sha256Hex.hash(email.getValue());
    }
}
