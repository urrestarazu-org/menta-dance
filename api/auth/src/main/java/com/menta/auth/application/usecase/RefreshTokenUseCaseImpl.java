package com.menta.auth.application.usecase;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.dto.RefreshCommand;
import com.menta.auth.application.dto.TokenPair;
import com.menta.auth.application.port.in.RefreshTokenUseCase;
import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.IssuedAccessToken;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.application.port.out.RefreshTokenRepository;
import com.menta.auth.application.port.out.TokenHasher;
import com.menta.auth.domain.exception.AuthDegradedException;
import com.menta.auth.domain.exception.RefreshTokenCompromisedException;
import com.menta.auth.domain.model.RefreshToken;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of the refresh-rotation use case.
 *
 * Decision order (fail-closed, ADR-0026 + spec scenarios):
 *   1. Degraded reconciler → AuthDegradedException.
 *   2. Unknown hash → compromise exception (no event, no info leak).
 *   3. REVOKED → immutability exception (no event).
 *   4. Expired → compromise with "expired" detail (no state mutation).
 *   5. USED → family revoke + tokenVersion++ + REFRESH_REVOKED event.
 *   6. tokenVersion mismatch → family revoke + REFRESH_REVOKED event (user
 *      stays at its current higher version; we do NOT down-bump).
 *   7. ACTIVE valid → mark USED, mint next refresh in same family, mint
 *      access token, append REFRESH_ROTATED event.
 */
public class RefreshTokenUseCaseImpl implements RefreshTokenUseCase {

    /**
     * Refresh TTL: 7 days per ADR-0025. Hard-coded for PR1.
     */
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenIssuer accessTokenIssuer;
    private final TokenHasher tokenHasher;
    private final OutboxAppender outboxAppender;
    private final AuthDegradedGuard authDegradedGuard;

    public RefreshTokenUseCaseImpl(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        AccessTokenIssuer accessTokenIssuer,
        TokenHasher tokenHasher,
        OutboxAppender outboxAppender,
        AuthDegradedGuard authDegradedGuard
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenIssuer = accessTokenIssuer;
        this.tokenHasher = tokenHasher;
        this.outboxAppender = outboxAppender;
        this.authDegradedGuard = authDegradedGuard;
    }

    @Override
    public TokenPair execute(RefreshCommand command) {
        if (authDegradedGuard.isDegraded()) {
            throw new AuthDegradedException();
        }

        String presentedHash = tokenHasher.hash(command.rawRefreshToken());
        Optional<RefreshToken> maybe = refreshTokenRepository.findByHash(presentedHash);
        if (maybe.isEmpty()) {
            // Unknown hash — treat as compromise to avoid revealing existence.
            throw new RefreshTokenCompromisedException();
        }
        RefreshToken presented = maybe.get();

        // REVOKED is terminal (spec: "inmutable").
        if (presented.isRevoked()) {
            throw new RefreshTokenCompromisedException();
        }

        // Expired but ACTIVE: reject without mutating storage status.
        if (presented.isExpired(Instant.now())) {
            throw new RefreshTokenCompromisedException();
        }

        Optional<User> maybeUser = userRepository.findById(presented.getUserId());
        User user = maybeUser.orElseThrow(RefreshTokenCompromisedException::new);

        // USED → family revoke + bump token_version + REFRESH_REVOKED.
        if (presented.isCompromised()) {
            revokeFamilyAndBump(presented.getFamilyId(), user,
                "compromised");
            throw new RefreshTokenCompromisedException();
        }

        // tokenVersion mismatch → family revoke WITHOUT bumping (user already higher).
        if (presented.getTokenVersion() != user.getTokenVersion()) {
            refreshTokenRepository.revokeFamily(presented.getFamilyId());
            outboxAppender.append(
                AuthOutboxEventTypes.REFRESH_REVOKED,
                presented.getFamilyId().toString(),
                "{\"reason\":\"stale_token_version\",\"userTokenVersion\":" + user.getTokenVersion() + "}"
            );
            throw new RefreshTokenCompromisedException();
        }

        // ACTIVE + valid → rotate.
        presented.markUsed();
        // Persist the USED transition. The bulk-revoke paths go through
        // refreshTokenRepository#revokeFamily (JPQL bulk update). The
        // happy-rotation path mutates only the in-memory aggregate; without
        // a save() here the row stays ACTIVE in MySQL and the same refresh
        // could be presented again. See LogoutUseCaseImpl for the matching
        // fix and the test that surfaced it.
        refreshTokenRepository.save(presented);

        UUID nextRefreshId = UUID.randomUUID();
        String nextHash = tokenHasher.hash(nextRefreshId.toString());
        Instant nextExpiresAt = Instant.now().plus(REFRESH_TTL);
        RefreshToken next = RefreshToken.rotate(
            presented.getUserId(),
            presented.getFamilyId(),
            nextHash,
            user.getTokenVersion(),
            nextExpiresAt
        );
        refreshTokenRepository.save(next);

        outboxAppender.append(
            AuthOutboxEventTypes.REFRESH_ROTATED,
            nextRefreshId.toString(),
            "{\"familyId\":\"" + presented.getFamilyId() + "\",\"tokenVersion\":" + user.getTokenVersion() + "}"
        );

        IssuedAccessToken access = accessTokenIssuer.issue(user);
        return new TokenPair(
            access.token(),
            nextRefreshId.toString(),
            TokenPair.TOKEN_TYPE_BEARER,
            access.ttl()
        );
    }

    private void revokeFamilyAndBump(UUID familyId, User user, String reason) {
        user.bumpTokenVersion();
        userRepository.save(user);
        refreshTokenRepository.revokeFamily(familyId);
        outboxAppender.append(
            AuthOutboxEventTypes.REFRESH_REVOKED,
            familyId.toString(),
            // userId is required by the reconciler to project tokenVersion to
            // Redis (#88). The aggregateId here is a familyId, not a jti — no
            // access-token identifier exists to key a blacklist entry on.
            "{\"userId\":\"" + user.getId().getValue() + "\",\"reason\":\"" + reason
                + "\",\"newTokenVersion\":" + user.getTokenVersion() + "}"
        );
    }
}
