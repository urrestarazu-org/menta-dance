package com.menta.auth.application.usecase;

import com.menta.auth.application.dto.LoginCommand;
import com.menta.auth.application.dto.TokenPair;
import com.menta.auth.application.port.in.LoginUseCase;
import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.IssuedAccessToken;
import com.menta.auth.application.port.out.LoginAttemptAuditPort;
import com.menta.auth.application.port.out.LoginAttemptOutcome;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.application.port.out.RefreshTokenRepository;
import com.menta.auth.application.port.out.TokenHasher;
import com.menta.auth.domain.crypto.Sha256Hex;
import com.menta.auth.domain.exception.AccountNotActivatedException;
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
 * <p>Pure application orchestration — no Spring, no JPA, no framework imports
 * (ArchUnit-enforced by ArchitectureTest).
 *
 * <p>Decision order (fail-closed):
 *   1. Degraded guard blocks everything (ADR-0026 → 503 upstream).
 *   2. Failure budget already spent → LoginRateLimitedException (429 upstream).
 *   3. Unknown email → InvalidCredentialsException (no discrimination).
 *   4. Wrong password (any account status) → InvalidCredentialsException (no
 *      discrimination); counted as a failure.
 *   5. Correct password, decision by status (revealed only now, never without the password):
 *      PENDING_ACTIVATION → AccountNotActivatedException (403); LOCKED →
 *      LockedUserException (423); INACTIVE / SUSPENDED → InvalidCredentialsException (401).
 *      None of these consume the failure budget.
 *   6. ACTIVE: issue access, mint refresh in new family. No outbox event is written: the former
 *      {@code auth.AuthUserLoggedIn} had no consumer (#672).
 *
 * <p>Budgets count failures, never attempts (ADR-0035). Step 2 only reads, so a
 * successful login costs nothing; the counters move in the failure branches.
 * Checking before the repository lookup keeps a throttled caller to one Redis
 * round-trip instead of a query plus a deliberately slow bcrypt comparison.
 *
 * <p>Volumetric protection is NGINX's job and already happened at the edge. What
 * is left here is the judgement only this layer can make: whether the attempt
 * actually failed.
 *
 * <p>Every judged attempt is audited through LoginAttemptAuditPort, which writes
 * outside transactional control — a failed login rolls the transaction back,
 * so a transactional write could not carry that trail.
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
    private final AuthDegradedGuard authDegradedGuard;
    private final LoginRateLimitPort loginRateLimitPort;
    private final LoginAttemptAuditPort loginAttemptAuditPort;

    public LoginUseCaseImpl(
        UserRepository userRepository,
        PasswordEncoderPort passwordEncoder,
        AccessTokenIssuer accessTokenIssuer,
        TokenHasher tokenHasher,
        RefreshTokenRepository refreshTokenRepository,
        AuthDegradedGuard authDegradedGuard,
        LoginRateLimitPort loginRateLimitPort,
        LoginAttemptAuditPort loginAttemptAuditPort
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.tokenHasher = tokenHasher;
        this.refreshTokenRepository = refreshTokenRepository;
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

        // Password first: everything below this line is only reachable by someone who
        // knows the credentials, so revealing the account state cannot enumerate accounts.
        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            countFailure(LoginAttemptOutcome.INVALID_CREDENTIALS, emailFingerprint, clientFingerprint);
            throw new InvalidCredentialsException();
        }

        rejectNonOperationalAccount(user, emailFingerprint, clientFingerprint);

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

        IssuedAccessToken access = accessTokenIssuer.issue(user);

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
     * Decides by account status once the password is known to be correct. These rejections
     * are audited but NOT charged to the failure budget: the caller proved knowledge of the
     * credentials, so this is not guessing (ADR-0035 counts failures to credentials).
     */
    private void rejectNonOperationalAccount(
        User user, String emailFingerprint, String clientFingerprint
    ) {
        UserStatus status = user.getStatus();
        if (status == UserStatus.ACTIVE) {
            return;
        }
        if (status == UserStatus.PENDING_ACTIVATION) {
            audit(LoginAttemptOutcome.ACCOUNT_NOT_ACTIVATED, emailFingerprint, clientFingerprint);
            throw new AccountNotActivatedException(user.getId().getValue());
        }
        if (status == UserStatus.LOCKED) {
            audit(LoginAttemptOutcome.LOCKED, emailFingerprint, clientFingerprint);
            throw new LockedUserException(user.getId().getValue());
        }
        // INACTIVE and SUSPENDED: generic 401 even with the right password. Revealing an
        // administrative suspension is a separate product decision (issue #477).
        audit(LoginAttemptOutcome.ACCOUNT_NOT_OPERATIONAL, emailFingerprint, clientFingerprint);
        throw new InvalidCredentialsException();
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
