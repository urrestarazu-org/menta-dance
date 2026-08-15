package com.menta.app.outbox;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Projects security-token events into the Redis JTI blacklist. */
@Component
public class BlacklistOutboxEventHandler implements OutboxEventHandler {

    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
        AuthOutboxEventTypes.AUTH_USER_LOGGED_IN,
        AuthOutboxEventTypes.REFRESH_ROTATED,
        AuthOutboxEventTypes.REFRESH_REVOKED,
        AuthOutboxEventTypes.USER_LOGGED_OUT
    );

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
