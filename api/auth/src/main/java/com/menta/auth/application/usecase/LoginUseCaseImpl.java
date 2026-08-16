package com.menta.auth.application.usecase;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.dto.LoginCommand;
import com.menta.auth.application.dto.TokenPair;
import com.menta.auth.application.port.in.LoginUseCase;
import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.IssuedAccessToken;
import com.menta.auth.application.port.out.LoginAttemptAuditPort;
import com.menta.auth.application.port.out.LoginAttemptOutcome;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.application.port.out.RefreshTokenRepository;
import com.menta.auth.application.port.out.TokenHasher;
import com.menta.auth.domain.crypto.Sha256Hex;
import com.menta.auth.domain.exception.AuthDegradedException;
import com.menta.auth.domain.exception.InvalidCredentialsException;
import com.menta.auth.domain.exception.LockedUserException;
import com.menta.auth.domain.exception.LoginRateLimitedException;
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
 *   2. Failure budget already spent → LoginRateLimitedException (429 upstream).
 *   3. Unknown email → InvalidCredentialsException (no discrimination).
 *   4. LOCKED user → LockedUserException, no outbox emit.
 *   5. Wrong password → InvalidCredentialsException (no discrimination).
 *   6. Otherwise: issue access, mint refresh in new family, append outbox.
 *
 * Budgets count failures, never attempts (ADR-0035). Step 2 only reads, so a
 * successful login costs nothing; the counters move in the failure branches.
 * Checking before the repository lookup keeps a throttled caller to one Redis
 * round-trip instead of a query plus a deliberately slow bcrypt comparison.
 *
 * Volumetric protection is NGINX's job and already happened at the edge. What
 * is left here is the judgement only this layer can make: whether the attempt
 * actually failed.
 *
 * Every judged attempt is audited through LoginAttemptAuditPort, which writes
 * outside transactional control — a failed login rolls the transaction back,
 * so the outbox cannot carry that trail.
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
    private final LoginRateLimitPort loginRateLimitPort;
    private final LoginAttemptAuditPort loginAttemptAuditPort;

    public LoginUseCaseImpl(
        UserRepository userRepository,
        PasswordEncoderPort passwordEncoder,
        AccessTokenIssuer accessTokenIssuer,
        TokenHasher tokenHasher,
        RefreshTokenRepository refreshTokenRepository,
        OutboxAppender outboxAppender,
        AuthDegradedGuard authDegradedGuard,
        LoginRateLimitPort loginRateLimitPort,
        LoginAttemptAuditPort loginAttemptAuditPort
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.tokenHasher = tokenHasher;
        this.refreshTokenRepository = refreshTokenRepository;
        this.outboxAppender = outboxAppender;
        this.authDegradedGuard = authDegradedGuard;
        this.loginRateLimitPort = loginRateLimitPort;
        this.loginAttemptAuditPort = loginAttemptAuditPort;
    }

    @Override
    public TokenPair execute(LoginCommand command) {
        // Fail-closed: degraded reconciler blocks everything before any
        // side-effect (auth-login spec: "Reconciliador atrasado produce 503").
        if (authDegradedGuard.isDegraded()) {
            throw new AuthDegradedException();
        }

        Email email = Email.of(command.email());
        String emailFingerprint = emailFingerprint(email);
        String clientFingerprint = command.clientFingerprint();

        // Read-only, and before both the lookup and bcrypt.
        RateLimitDecision decision = loginRateLimitPort.check(emailFingerprint, clientFingerprint);
        if (!decision.isAllowed()) {
            audit(LoginAttemptOutcome.RATE_LIMITED, emailFingerprint, clientFingerprint);
            throw new LoginRateLimitedException(decision.getRetryAfter());
        }

        // Spec: "401 sin discriminar" — unknown email MUST look the same as
        // wrong password to the caller. We unify under InvalidCredentialsException.
        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) {
            countFailure(LoginAttemptOutcome.INVALID_CREDENTIALS, emailFingerprint, clientFingerprint);
            throw new InvalidCredentialsException();
        }
        User user = maybeUser.get();

        // LOCKED is a dedicated signal — distinct from generic 401 — so the
        // controller can return 423 Locked. Reaching this branch is always an
        // administrative decision: this throttle never sets that status.
        if (user.getStatus() == UserStatus.LOCKED) {
            countFailure(LoginAttemptOutcome.LOCKED, emailFingerprint, clientFingerprint);
            throw new LockedUserException(user.getId().getValue());
        }

        // Pending, inactive, and suspended accounts receive the same generic
        // response as invalid credentials to avoid leaking account state.
        if (!user.isActive()) {
            countFailure(LoginAttemptOutcome.INVALID_CREDENTIALS, emailFingerprint, clientFingerprint);
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            countFailure(LoginAttemptOutcome.INVALID_CREDENTIALS, emailFingerprint, clientFingerprint);
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

        // Only the email budget is cleared. The origin budget deliberately
        // survives: clearing it would let an attacker with one valid account
        // of their own wipe their origin counter at will and keep spraying.
        loginRateLimitPort.resetEmail(emailFingerprint);
        audit(LoginAttemptOutcome.SUCCESS, emailFingerprint, clientFingerprint);

        return new TokenPair(
            access.token(),
            refreshId.toString(),
            TokenPair.TOKEN_TYPE_BEARER,
            access.ttl()
        );
    }

    /**
     * A countable failure: charge both budgets, then leave the audit trail.
     * Recording happens only here, never on the success path.
     */
    private void countFailure(
        LoginAttemptOutcome outcome, String emailFingerprint, String clientFingerprint
    ) {
        loginRateLimitPort.recordFailure(emailFingerprint, clientFingerprint);
        audit(outcome, emailFingerprint, clientFingerprint);
    }

    private void audit(
        LoginAttemptOutcome outcome, String emailFingerprint, String clientFingerprint
    ) {
        loginAttemptAuditPort.record(outcome, emailFingerprint, clientFingerprint);
    }

    /**
     * Fingerprints the normalized address, never the raw one, so the budgets
     * and the audit trail share a key that cannot be reversed into an email.
     */
    private static String emailFingerprint(Email email) {
        return Sha256Hex.hash(email.getValue());
    }
}
