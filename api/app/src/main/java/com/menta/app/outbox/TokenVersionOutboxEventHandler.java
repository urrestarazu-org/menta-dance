package com.menta.app.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Projects the user's current {@code tokenVersion} into Redis so
 * {@code JwtAuthenticationFilter} can reject already-revoked access tokens
 * without a SQL read per request (#88).
 *
 * <p>This is the mechanism that actually closes access tokens on logout,
 * refresh-reuse detection and password reset. The jti blacklist cannot do it:
 * no access-token identifier is persisted anywhere, so those events carry a
 * familyId or a refresh id, never a revocable jti.</p>
 *
 * <p>Idempotent by construction — the projection is a {@code SET} of the
 * version carried in the payload, so replaying a row writes the same value.
 * Redis failures propagate so the worker marks the row FAILED and retries with
 * backoff; swallowing them would leave revoked tokens usable while the row
 * reported success.</p>
 */
@Component
public class TokenVersionOutboxEventHandler implements OutboxEventHandler {

    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
        AuthOutboxEventTypes.REFRESH_REVOKED,
        AuthOutboxEventTypes.USER_LOGGED_OUT,
        AuthOutboxEventTypes.PASSWORD_RESET_COMPLETED
    );

    private static final String USER_ID_FIELD = "userId";
    /** REFRESH_REVOKED and PASSWORD_RESET_COMPLETED name it newTokenVersion; USER_LOGGED_OUT, tokenVersion. */
    private static final String NEW_TOKEN_VERSION_FIELD = "newTokenVersion";
    private static final String TOKEN_VERSION_FIELD = "tokenVersion";

    private final TokenBlacklistPort tokenBlacklistPort;
    private final ObjectMapper objectMapper;

    public TokenVersionOutboxEventHandler(
        TokenBlacklistPort tokenBlacklistPort, ObjectMapper objectMapper
    ) {
        this.tokenBlacklistPort = tokenBlacklistPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType) {
        return SUPPORTED_EVENT_TYPES.contains(eventType);
    }

    @Override
    public void handle(OutboxRowJpaEntity row) {
        JsonNode payload = readPayload(row);
        String userId = requiredText(payload, row);
        long tokenVersion = requiredTokenVersion(payload, row);
        tokenBlacklistPort.projectTokenVersion(userId, tokenVersion);
    }

    private JsonNode readPayload(OutboxRowJpaEntity row) {
        try {
            return objectMapper.readTree(row.getPayload());
        } catch (Exception malformed) {
            // Failing loudly is correct: a row we cannot read is a row whose
            // revocation never reaches Redis. The worker retries it.
            throw new IllegalStateException(
                "Unreadable outbox payload for event " + row.getEventType(), malformed
            );
        }
    }

    private static String requiredText(JsonNode payload, OutboxRowJpaEntity row) {
        JsonNode value = payload.get(USER_ID_FIELD);
        if (value == null || value.asText().isBlank()) {
            throw new IllegalStateException(
                "Missing userId in payload for event " + row.getEventType()
            );
        }
        return value.asText();
    }

    private static long requiredTokenVersion(JsonNode payload, OutboxRowJpaEntity row) {
        JsonNode value = payload.get(NEW_TOKEN_VERSION_FIELD);
        if (value == null) {
            value = payload.get(TOKEN_VERSION_FIELD);
        }
        if (value == null || !value.canConvertToLong()) {
            throw new IllegalStateException(
                "Missing tokenVersion in payload for event " + row.getEventType()
            );
        }
        return value.asLong();
    }
}
