package com.menta.auth.application.usecase;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.dto.LoginCommand;
import com.menta.auth.application.dto.TokenPair;
import com.menta.auth.application.port.in.LoginUseCase;
import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.IssuedAccessToken;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.application.port.out.RefreshTokenRepository;
import com.menta.auth.application.port.out.TokenHasher;
import com.menta.auth.domain.exception.AuthDegradedException;
import com.menta.auth.domain.exception.InvalidCredentialsException;
import com.menta.auth.domain.exception.LockedUserException;
import com.menta.auth.domain.model.RefreshToken;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of the login use case.
 *
 * Pure application orchestration — no Spring, no JPA, no framework imports
 * (ArchUnit-enforced by ArchitectureTest).
 *
 * Decision order (fail-closed):
 *   1. Degraded guard blocks everything (ADR-0026 → 503 upstream).
 *   2. Unknown email → InvalidCredentialsException (no discrimination).
 *   3. LOCKED user → LockedUserException, no outbox emit.
 *   4. Wrong password → InvalidCredentialsException (no discrimination).
 *   5. Otherwise: issue access, mint refresh in new family, append outbox.
 */
public class LoginUseCaseImpl implements LoginUseCase {

    /**
     * Refresh TTL: 7 days per ADR-0025. Hard-coded for PR1 — promoted to a
     * configuration property in PR3 wiring if needed.
     */
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final TokenHasher tokenHasher;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OutboxAppender outboxAppender;
    private final AuthDegradedGuard authDegradedGuard;

    public LoginUseCaseImpl(
        UserRepository userRepository,
        PasswordEncoderPort passwordEncoder,
        AccessTokenIssuer accessTokenIssuer,
        TokenHasher tokenHasher,
        RefreshTokenRepository refreshTokenRepository,
        OutboxAppender outboxAppender,
        AuthDegradedGuard authDegradedGuard
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.tokenHasher = tokenHasher;
        this.refreshTokenRepository = refreshTokenRepository;
        this.outboxAppender = outboxAppender;
        this.authDegradedGuard = authDegradedGuard;
    }

    @Override
    public TokenPair execute(LoginCommand command) {
        // Fail-closed: degraded reconciler blocks everything before any
        // side-effect (auth-login spec: "Reconciliador atrasado produce 503").
        if (authDegradedGuard.isDegraded()) {
            throw new AuthDegradedException();
        }

        Email email = Email.of(command.email());

        // Spec: "401 sin discriminar" — unknown email MUST look the same as
        // wrong password to the caller. We unify under InvalidCredentialsException.
        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) {
            throw new InvalidCredentialsException();
        }
        User user = maybeUser.get();

        // LOCKED is a dedicated signal — distinct from generic 401 — so the
        // controller can return 423 Locked.
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new LockedUserException(user.getId().getValue());
        }

        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        IssuedAccessToken access = accessTokenIssuer.issue(user);

        UUID refreshId = UUID.randomUUID();
        String refreshHash = tokenHasher.hash(refreshId.toString());
        Instant refreshExpiresAt = Instant.now().plus(REFRESH_TTL);

        RefreshToken newRefresh = RefreshToken.newFamily(
            user.getId(),
            refreshHash,
            user.getTokenVersion(),
            refreshExpiresAt
        );
        refreshTokenRepository.save(newRefresh);

        // ADR-0027: outbox row in the SAME transaction as the refresh insert.
        // The reconciler pulls PENDING rows asynchronously and projects the
        // blacklist side-effect to Redis.
        String payload = "{\"tokenVersion\":" + user.getTokenVersion() + "}";
        outboxAppender.append(
            AuthOutboxEventTypes.AUTH_USER_LOGGED_IN,
            access.jti(),
            payload
        );

        return new TokenPair(
            access.token(),
            refreshId.toString(),
            TokenPair.TOKEN_TYPE_BEARER,
            access.ttl()
        );
    }
}
