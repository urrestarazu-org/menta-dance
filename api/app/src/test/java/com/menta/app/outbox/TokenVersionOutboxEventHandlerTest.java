package com.menta.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.shared.outbox.OutboxStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The mechanism that actually closes access tokens on revocation (#88). */
@ExtendWith(MockitoExtension.class)
class TokenVersionOutboxEventHandlerTest {

    private static final UUID USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID FAMILY_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID REFRESH_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

    @Mock private TokenBlacklistPort tokenBlacklistPort;

    private TokenVersionOutboxEventHandler handler() {
        return new TokenVersionOutboxEventHandler(tokenBlacklistPort, new ObjectMapper());
    }

    @Nested
    @DisplayName("Eventos soportados")
    class SupportedEvents {

        @Test
        void claims_every_event_that_bumps_token_version() {
            TokenVersionOutboxEventHandler handler = handler();

            assertThat(handler.supports(AuthOutboxEventTypes.REFRESH_REVOKED)).isTrue();
            assertThat(handler.supports(AuthOutboxEventTypes.USER_LOGGED_OUT)).isTrue();
            assertThat(handler.supports(AuthOutboxEventTypes.PASSWORD_RESET_COMPLETED)).isTrue();
        }

        @Test
        void does_not_claim_events_that_do_not_revoke_anything() {
            // A successful login or a normal rotation issues tokens; it does
            // not invalidate previously issued ones.
            TokenVersionOutboxEventHandler handler = handler();

            assertThat(handler.supports(AuthOutboxEventTypes.AUTH_USER_LOGGED_IN)).isFalse();
            assertThat(handler.supports(AuthOutboxEventTypes.REFRESH_ROTATED)).isFalse();
            assertThat(handler.supports(AuthOutboxEventTypes.PASSWORD_RESET_REQUESTED)).isFalse();
        }
    }

    @Nested
    @DisplayName("Proyección")
    class Projection {

        @Test
        void projects_the_new_version_from_a_refresh_revoked_payload() {
            handler().handle(refreshRevokedRow(4L));

            verify(tokenBlacklistPort).projectTokenVersion(USER_ID.toString(), 4L);
        }

        @Test
        void projects_the_version_from_a_logout_payload() {
            // The two events name the field differently; both must project.
            handler().handle(logoutRow(3L));

            verify(tokenBlacklistPort).projectTokenVersion(USER_ID.toString(), 3L);
        }

        @Test
        void projects_the_new_version_from_a_password_reset_completed_payload() {
            OutboxRowJpaEntity row = row(
                AuthOutboxEventTypes.PASSWORD_RESET_COMPLETED, USER_ID.toString(),
                "{\"userId\":\"" + USER_ID + "\",\"newTokenVersion\":5}"
            );

            handler().handle(row);

            verify(tokenBlacklistPort).projectTokenVersion(USER_ID.toString(), 5L);
        }

        @Test
        void is_idempotent_across_replays() {
            // The worker re-picks PENDING rows after a crash; replaying must
            // land the same value, never increment anything.
            TokenVersionOutboxEventHandler handler = handler();

            handler.handle(refreshRevokedRow(4L));
            handler.handle(refreshRevokedRow(4L));

            verify(tokenBlacklistPort, times(2)).projectTokenVersion(USER_ID.toString(), 4L);
        }

        @Test
        void never_writes_a_blacklist_entry() {
            // The projection and the jti blacklist are separate namespaces;
            // conflating them is the #88 regression.
            handler().handle(refreshRevokedRow(4L));

            verify(tokenBlacklistPort, never()).blacklist(anyString(), org.mockito.ArgumentMatchers.any());
        }

        @Test
        void never_projects_the_family_id_as_a_user_id() {
            // aggregateId of REFRESH_REVOKED is a familyId — using it as the
            // projection key would write a version nobody ever reads.
            handler().handle(refreshRevokedRow(4L));

            verify(tokenBlacklistPort, never())
                .projectTokenVersion(org.mockito.ArgumentMatchers.eq(FAMILY_ID.toString()), anyLong());
        }
    }

    @Nested
    @DisplayName("Fail-closed")
    class FailClosed {

        @Test
        void propagates_redis_failures_so_the_worker_retries() {
            doThrow(new RuntimeException("connection refused"))
                .when(tokenBlacklistPort).projectTokenVersion(anyString(), anyLong());

            assertThatThrownBy(() -> handler().handle(refreshRevokedRow(4L)))
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void rejects_a_payload_without_user_id() {
            OutboxRowJpaEntity row = row(
                AuthOutboxEventTypes.REFRESH_REVOKED, FAMILY_ID.toString(),
                "{\"reason\":\"compromised\",\"newTokenVersion\":4}"
            );

            assertThatThrownBy(() -> handler().handle(row))
                .isInstanceOf(IllegalStateException.class);
            verify(tokenBlacklistPort, never()).projectTokenVersion(anyString(), anyLong());
        }

        @Test
        void rejects_a_payload_without_a_token_version() {
            OutboxRowJpaEntity row = row(
                AuthOutboxEventTypes.REFRESH_REVOKED, FAMILY_ID.toString(),
                "{\"userId\":\"" + USER_ID + "\",\"reason\":\"compromised\"}"
            );

            assertThatThrownBy(() -> handler().handle(row))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void rejects_an_unreadable_payload() {
            // A row we cannot parse is a revocation that never reaches Redis;
            // failing marks it FAILED for retry instead of silently COMPLETED.
            OutboxRowJpaEntity row = row(
                AuthOutboxEventTypes.REFRESH_REVOKED, FAMILY_ID.toString(), "not json"
            );

            assertThatThrownBy(() -> handler().handle(row))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    private static OutboxRowJpaEntity refreshRevokedRow(long newTokenVersion) {
        return row(
            AuthOutboxEventTypes.REFRESH_REVOKED, FAMILY_ID.toString(),
            "{\"userId\":\"" + USER_ID + "\",\"reason\":\"compromised\",\"newTokenVersion\":"
                + newTokenVersion + "}"
        );
    }

    private static OutboxRowJpaEntity logoutRow(long tokenVersion) {
        return row(
            AuthOutboxEventTypes.USER_LOGGED_OUT, REFRESH_ID.toString(),
            "{\"userId\":\"" + USER_ID + "\",\"familyId\":\"" + FAMILY_ID
                + "\",\"tokenVersion\":" + tokenVersion + "}"
        );
    }

    private static OutboxRowJpaEntity row(String eventType, String aggregateId, String payload) {
        return new OutboxRowJpaEntity(
            "01H9X3F4Z9YJ7K5Q6T2R8V1N8T", eventType, aggregateId, payload,
            OutboxStatus.PENDING, 0, null, null, Instant.parse("2026-08-16T12:00:00Z"), null
        );
    }
}
