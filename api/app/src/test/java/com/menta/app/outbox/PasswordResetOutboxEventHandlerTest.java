package com.menta.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.port.out.PasswordResetNotificationPort;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.shared.outbox.OutboxStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PasswordResetOutboxEventHandlerTest {

    private static final UUID TOKEN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock private PasswordResetNotificationPort notificationPort;

    @Test
    void sends_the_password_reset_notification_for_its_exact_event_type() {
        PasswordResetOutboxEventHandler handler =
            new PasswordResetOutboxEventHandler(notificationPort);

        handler.handle(passwordResetRow(TOKEN_ID));

        assertThat(handler.supports(AuthOutboxEventTypes.PASSWORD_RESET_REQUESTED)).isTrue();
        verify(notificationPort).sendPasswordResetEmail(TOKEN_ID);
    }

    @Test
    void does_not_claim_event_types_owned_by_other_handlers() {
        // The worker throws if two handlers match one event type, so an
        // over-broad supports() would break dispatch for every row.
        PasswordResetOutboxEventHandler handler =
            new PasswordResetOutboxEventHandler(notificationPort);

        assertThat(handler.supports(AuthOutboxEventTypes.ACCOUNT_ACTIVATION_REQUESTED)).isFalse();
        assertThat(handler.supports(AuthOutboxEventTypes.AUTH_USER_LOGGED_IN)).isFalse();
        assertThat(handler.supports(AuthOutboxEventTypes.REFRESH_REVOKED)).isFalse();
        assertThat(handler.supports(AuthOutboxEventTypes.USER_LOGGED_OUT)).isFalse();
    }

    @Test
    void propagates_notification_failures_so_the_worker_marks_the_row_failed() {
        // Swallowing here would mark the row COMPLETED and lose the reset
        // email permanently — the raw token exists nowhere else.
        PasswordResetOutboxEventHandler handler =
            new PasswordResetOutboxEventHandler(notificationPort);
        RuntimeException smtpDown = new RuntimeException("smtp unavailable");
        doThrow(smtpDown).when(notificationPort).sendPasswordResetEmail(TOKEN_ID);

        assertThatThrownBy(() -> handler.handle(passwordResetRow(TOKEN_ID))).isSameAs(smtpDown);
    }

    private static OutboxRowJpaEntity passwordResetRow(UUID tokenId) {
        return new OutboxRowJpaEntity(
            "01H9X3F4Z9YJ7K5Q6T2R8V1N5Q", AuthOutboxEventTypes.PASSWORD_RESET_REQUESTED,
            tokenId.toString(), "{\"passwordResetTokenId\":\"" + tokenId + "\"}",
            OutboxStatus.PENDING, 0, null, null, Instant.parse("2026-08-16T12:00:00Z"), null
        );
    }
}
