package com.menta.app.outbox;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Projects security-token events into the Redis JTI blacklist.
 *
 * <h2>Why this handler currently claims nothing (#88)</h2>
 *
 * <p>It used to blacklist {@code row.getAggregateId()} for four event types.
 * None of the four was correct:</p>
 *
 * <ul>
 *   <li>{@code REFRESH_REVOKED} carries a {@code familyId}, so it wrote
 *       {@code blacklist:jti:{familyId}} — a key no real jti can match.</li>
 *   <li>{@code USER_LOGGED_OUT} carries the refresh token id — same dead
 *       key.</li>
 *   <li>{@code AUTH_USER_LOGGED_IN} and {@code REFRESH_ROTATED} carry a real
 *       jti, but it is the token minted by that very operation: had anything
 *       read the blacklist, every login would have produced a born-revoked
 *       access token.</li>
 * </ul>
 *
 * <p>No event transports a revocable jti today — {@code LogoutCommand} does
 * not even receive one — so jti-level revocation would require threading the
 * jti from the HTTP layer into the event. {@code tokenVersion} already covers
 * every revocation case (logout, refresh reuse, password reset) through
 * {@link TokenVersionOutboxEventHandler}, so nothing is claimed here rather
 * than writing keys that cannot match.</p>
 *
 * <p>The port and this handler are kept because per-token revocation without
 * a version bump is a legitimate future need; it just requires carrying the
 * jti first.</p>
 */
@Component
public class BlacklistOutboxEventHandler implements OutboxEventHandler {

    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of();

    private final TokenBlacklistPort tokenBlacklistPort;
    private final Duration accessTtl;

    /** Creates the blacklist handler with the configured access-token lifetime. */
    public BlacklistOutboxEventHandler(
        TokenBlacklistPort tokenBlacklistPort,
        @Value("${auth.access-token-ttl-seconds:900}") long accessTtlSeconds
    ) {
        this.tokenBlacklistPort = tokenBlacklistPort;
        this.accessTtl = Duration.ofSeconds(accessTtlSeconds);
    }

    @Override
    public boolean supports(String eventType) {
        return SUPPORTED_EVENT_TYPES.contains(eventType);
    }

    @Override
    public void handle(OutboxRowJpaEntity row) {
        tokenBlacklistPort.blacklist(row.getAggregateId(), accessTtl);
    }
}
