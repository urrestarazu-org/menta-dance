package com.menta.auth.application.usecase;

import com.menta.auth.application.dto.ResetPasswordCommand;
import com.menta.auth.application.port.in.ResetPasswordUseCase;
import com.menta.auth.application.port.out.Clock;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetTokenHasher;
import com.menta.auth.application.port.out.PasswordResetTokenRepository;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.application.port.out.RefreshTokenRepository;
import com.menta.auth.domain.crypto.Sha256Hex;
import com.menta.auth.domain.exception.PasswordResetRateLimitedException;
import com.menta.auth.domain.exception.PasswordResetTokenAlreadyUsedException;
import com.menta.auth.domain.exception.PasswordResetTokenExpiredException;
import com.menta.auth.domain.exception.PasswordResetTokenNotFoundException;
import com.menta.auth.domain.exception.SamePasswordException;
import com.menta.auth.domain.model.Password;
import com.menta.auth.domain.model.PasswordResetToken;
import com.menta.auth.domain.model.PasswordResetTokenStatus;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.repository.UserRepository;

import java.time.Instant;

/**
 * Implementation of ResetPasswordUseCase (US-AUTH-006).
 *
 * <p>Validation order is deliberate and security-relevant: the token's own
 * status is resolved first (not found / already used / expired-or-superseded
 * all collapse before any mutation), then the new password is validated
 * against the policy and against the current credential — and only once
 * every check has passed is the token consumed. A weak or repeated password
 * must not burn the user's one reset attempt (escenario 5: "El token NO debe
 * marcarse como usado"); a caller who mistypes a password gets to retry with
 * the same link.</p>
 *
 * <p>Atomicity across the token/user/refresh writes is NOT provided here: the
 * caller MUST wrap this use case with a transactional decorator, mirroring
 * every other write use case in this module.</p>
 */
public class ResetPasswordUseCaseImpl implements ResetPasswordUseCase {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetTokenHasher passwordResetTokenHasher;
    private final PasswordResetAttemptRateLimitPort attemptRateLimitPort;
    private final UserRepository userRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginRateLimitPort loginRateLimitPort;
    private final Clock clock;

    public ResetPasswordUseCaseImpl(
        PasswordResetTokenRepository passwordResetTokenRepository,
        PasswordResetTokenHasher passwordResetTokenHasher,
        PasswordResetAttemptRateLimitPort attemptRateLimitPort,
        UserRepository userRepository,
        PasswordEncoderPort passwordEncoder,
        RefreshTokenRepository refreshTokenRepository,
        LoginRateLimitPort loginRateLimitPort,
        Clock clock
    ) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordResetTokenHasher = passwordResetTokenHasher;
        this.attemptRateLimitPort = attemptRateLimitPort;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginRateLimitPort = loginRateLimitPort;
        this.clock = clock;
    }

    @Override
    public void reset(ResetPasswordCommand command) {
        RateLimitDecision decision = attemptRateLimitPort.consume(command.clientFingerprint());
        if (!decision.isAllowed()) {
            throw new PasswordResetRateLimitedException(decision.getRetryAfter());
        }

        String tokenHash = passwordResetTokenHasher.hash(command.rawToken());
        PasswordResetToken token = passwordResetTokenRepository.findByHash(tokenHash)
            .orElseThrow(PasswordResetTokenNotFoundException::new);

        Instant now = clock.now();
        requireActive(token, now);

        // Defensive: the token's userId always points at a real user, since
        // nothing deletes users. A miss here means data corruption, not a
        // caller error — collapsed into the same not-found response rather
        // than a 500, since neither case is actionable by the caller.
        User user = userRepository.findById(token.getUserId())
            .orElseThrow(PasswordResetTokenNotFoundException::new);

        // Password.of() throws WeakPasswordException before anything is
        // mutated — a rejected password must not cost the user their token.
        Password newPassword = Password.of(command.newPassword());
        if (passwordEncoder.matches(newPassword.value(), user.getPasswordHash())) {
            throw new SamePasswordException();
        }

        // Every check passed: now, and only now, consume the token.
        token.consume(now);
        if (!passwordResetTokenRepository.consumeIfActive(token, now)) {
            // Lost a race against a concurrent reset with the same token.
            throw new PasswordResetTokenExpiredException();
        }

        String newPasswordHash = passwordEncoder.encode(newPassword.value());
        // resetPassword() bumps tokenVersion in the same call — password
        // change and session invalidation are one business event, not two
        // calls a caller might forget to pair.
        user.resetPassword(newPasswordHash);
        userRepository.save(user);

        // Every refresh across every device — not just the family the leaked
        // token belonged to (escenario 1: "invalidar TODOS los refresh tokens").
        refreshTokenRepository.revokeAllByUserId(user.getId());

        // Translated from the issue's "reiniciar failedLoginAttempts y
        // desbloquear la cuenta": that state does not exist (ADR-0035 — the
        // login throttle never locks the account). The real equivalent is
        // clearing the Redis failure budget, so recovering access does not
        // leave the user throttled by their own earlier mistakes.
        loginRateLimitPort.resetEmail(emailFingerprint(user));
    }

    private static void requireActive(PasswordResetToken token, Instant now) {
        PasswordResetTokenStatus status = token.statusAt(now);
        if (status == PasswordResetTokenStatus.USED) {
            throw new PasswordResetTokenAlreadyUsedException();
        }
        if (status != PasswordResetTokenStatus.ACTIVE) {
            // EXPIRED or INVALIDATED both read as "expired, request a new
            // one" — an invalidated (superseded) token was never used by its
            // holder, so claiming otherwise would be false.
            throw new PasswordResetTokenExpiredException();
        }
    }

    private static String emailFingerprint(User user) {
        return Sha256Hex.hash(user.getEmail().getValue());
    }
}
