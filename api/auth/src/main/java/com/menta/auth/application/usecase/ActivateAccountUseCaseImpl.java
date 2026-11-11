package com.menta.auth.application.usecase;

import com.menta.auth.application.dto.ActivateAccountCommand;
import com.menta.auth.application.port.in.ActivateAccountUseCase;
import com.menta.auth.application.port.out.ActivationTokenHasher;
import com.menta.auth.application.port.out.ActivationTokenRepository;
import com.menta.auth.application.port.out.Clock;
import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.domain.exception.ActivationTokenInvalidException;
import com.menta.auth.domain.exception.PasswordRequiredException;
import com.menta.auth.domain.model.ActivationToken;
import com.menta.auth.domain.model.ActivationTokenStatus;
import com.menta.auth.domain.model.Password;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.repository.UserRepository;
import java.time.Instant;

/**
 * Implementation of ActivateAccountUseCase.
 *
 * <p>Delegates every lifecycle rule to the aggregates themselves —
 * {@link ActivationToken#statusAt(Instant)}/{@link ActivationToken#consume(Instant)}
 * for expiry/use/invalidation, {@link User#activate()} for the status
 * transition — instead of reimplementing them here (auth-account-activation
 * design: "Flujo de activación"). Any failure collapses into the single
 * generic {@link ActivationTokenInvalidException} so a caller cannot
 * distinguish "not found" from "expired" from "already used" from "racing
 * activation" over the wire. Atomicity across the token/user writes is NOT
 * provided here: the caller MUST wrap this use case with a transactional
 * decorator (see {@code TransactionalActivateAccountUseCase}).</p>
 *
 * <p>An account created by an administrator has no usable password yet: activation requires the one its holder
 * chose, checked against the same policy as a password reset, and stores it in the same transaction that
 * activates the account. A missing or weak password is rejected before the token is consumed, so the holder can
 * retry with the same link. For every other account the password in the command is ignored.</p>
 */
public class ActivateAccountUseCaseImpl implements ActivateAccountUseCase {

    private final ActivationTokenRepository activationTokenRepository;
    private final ActivationTokenHasher activationTokenHasher;
    private final UserRepository userRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final Clock clock;

    public ActivateAccountUseCaseImpl(
        ActivationTokenRepository activationTokenRepository,
        ActivationTokenHasher activationTokenHasher,
        UserRepository userRepository,
        PasswordEncoderPort passwordEncoder,
        Clock clock
    ) {
        this.activationTokenRepository = activationTokenRepository;
        this.activationTokenHasher = activationTokenHasher;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public void activate(ActivateAccountCommand command) {
        String tokenHash = activationTokenHasher.hash(command.rawToken());

        ActivationToken token = activationTokenRepository.findByHash(tokenHash)
            .orElseThrow(ActivationTokenInvalidException::new);

        Instant now = clock.now();
        if (token.statusAt(now) != ActivationTokenStatus.ACTIVE) {
            throw new ActivationTokenInvalidException();
        }

        User user = userRepository.findById(token.getUserId())
            .orElseThrow(ActivationTokenInvalidException::new);

        // Checked before anything is consumed: a missing or weak password must not cost the holder the token.
        String chosenPasswordHash = user.isMustSetPassword() ? hashChosenPassword(command.password()) : null;

        try {
            token.consume(now);
            if (chosenPasswordHash == null) {
                user.activate();
            } else {
                user.activateWithPassword(chosenPasswordHash);
            }
        } catch (IllegalStateException e) {
            // A racing activation (or any other inconsistent state) collapses
            // into the same generic exception — never a distinguishable one.
            throw new ActivationTokenInvalidException();
        }

        if (!activationTokenRepository.consumeIfActive(token, now)) {
            throw new ActivationTokenInvalidException();
        }
        userRepository.save(user);
    }

    private String hashChosenPassword(String rawPassword) {
        if (rawPassword == null) {
            throw new PasswordRequiredException();
        }
        return passwordEncoder.encode(Password.of(rawPassword).value());
    }
}
