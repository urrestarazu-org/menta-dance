package com.menta.auth.application.usecase;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.dto.LogoutCommand;
import com.menta.auth.application.port.in.LogoutUseCase;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.application.port.out.RefreshTokenRepository;
import com.menta.auth.application.port.out.TokenHasher;
import com.menta.auth.domain.exception.AuthDegradedException;
import com.menta.auth.domain.exception.RefreshTokenCompromisedException;
import com.menta.auth.domain.model.RefreshToken;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of the logout use case.
 *
 * Decision order:
 *   1. Degraded reconciler → AuthDegradedException.
 *   2. Unknown hash → RefreshTokenCompromisedException (no leak).
 *   3. Already REVOKED → already done, refuse without re-publishing event.
 *   4. USED → escalate to family revoke + tokenVersion++ + REFRESH_REVOKED
 *      event; throw RefreshTokenCompromisedException.
 *   5. ACTIVE → mark REVOKED + publish UserLoggedOut (atomic in same tx).
 */
public class LogoutUseCaseImpl implements LogoutUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHasher tokenHasher;
    private final OutboxAppender outboxAppender;
    private final AuthDegradedGuard authDegradedGuard;

    public LogoutUseCaseImpl(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        TokenHasher tokenHasher,
        OutboxAppender outboxAppender,
        AuthDegradedGuard authDegradedGuard
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenHasher = tokenHasher;
        this.outboxAppender = outboxAppender;
        this.authDegradedGuard = authDegradedGuard;
    }

    @Override
    public void execute(LogoutCommand command) {
        if (authDegradedGuard.isDegraded()) {
            throw new AuthDegradedException();
        }

        String presentedHash = tokenHasher.hash(command.rawRefreshToken());
        Optional<RefreshToken> maybe = refreshTokenRepository.findByHash(presentedHash);
        if (maybe.isEmpty()) {
            throw new RefreshTokenCompromisedException();
        }
        RefreshToken presented = maybe.get();

        // Already REVOKED → immutable (spec: "no se publica ningún evento
        // adicional"). RefreshCompromisedException surfaces the rejection so
        // the controller returns 401.
        if (presented.isRevoked()) {
            throw new RefreshTokenCompromisedException();
        }

        // USED → spec mandates family revoke + tokenVersion bump path. We
        // do NOT do a simple logout here; the original rotation path
        // resumes family revocation.
        if (presented.isCompromised()) {
            Optional<User> maybeUser = userRepository.findById(presented.getUserId());
            User user = maybeUser.orElseThrow(RefreshTokenCompromisedException::new);
            user.bumpTokenVersion();
            userRepository.save(user);
            refreshTokenRepository.revokeFamily(presented.getFamilyId());
            outboxAppender.append(
                AuthOutboxEventTypes.REFRESH_REVOKED,
                presented.getFamilyId().toString(),
                "{\"reason\":\"compromised_at_logout\",\"newTokenVersion\":"
                    + user.getTokenVersion() + "}"
            );
            throw new RefreshTokenCompromisedException();
        }

        // ACTIVE happy path: mark REVOKED + atomic UserLoggedOut event.
        presented.markRevoked();
        // Critical: persist the status change. findByHash() returns a
        // detached domain POJO (the JPA adapter materializes a fresh entity
        // via mapper and returns the POJO). Mutating the POJO in memory has
        // no persistence effect; without this save() call the row stays
        // ACTIVE in MySQL and the next presentation re-detects the same
        // refresh as ACTIVE rather than REVOKED. PR2's unit tests did not
        // catch this because they only verified in-memory state; the bug
        // surfaced in PR3's AuthFlowIntegrationTest.
        refreshTokenRepository.save(presented);
        outboxAppender.append(
            AuthOutboxEventTypes.USER_LOGGED_OUT,
            presented.getId().toString(),
            "{\"familyId\":\"" + presented.getFamilyId() + "\",\"tokenVersion\":"
                + presented.getTokenVersion() + "}"
        );
    }
}
