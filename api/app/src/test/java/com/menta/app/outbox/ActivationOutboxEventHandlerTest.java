package com.menta.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.port.out.ActivationNotificationPort;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.shared.outbox.OutboxStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivationOutboxEventHandlerTest {

    private static final UUID TOKEN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private ActivationNotificationPort notificationPort;

    @Test
    void sends_the_activation_notification_for_its_exact_event_type() {
        ActivationOutboxEventHandler handler = new ActivationOutboxEventHandler(notificationPort);
        OutboxRowJpaEntity row = activationRow(TOKEN_ID);

        handler.handle(row);

        assertThat(handler.supports(AuthOutboxEventTypes.ACCOUNT_ACTIVATION_REQUESTED)).isTrue();
        assertThat(handler.supports(AuthOutboxEventTypes.AUTH_USER_LOGGED_IN)).isFalse();
        verify(notificationPort).sendActivationEmail(TOKEN_ID);
    }

    @Test
    void propagates_notification_failures_so_the_worker_marks_the_row_failed() {
        ActivationOutboxEventHandler handler = new ActivationOutboxEventHandler(notificationPort);
        OutboxRowJpaEntity row = activationRow(TOKEN_ID);
        RuntimeException smtpDown = new RuntimeException("smtp unavailable");
        doThrow(smtpDown).when(notificationPort).sendActivationEmail(TOKEN_ID);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> handler.handle(row))
            .isSameAs(smtpDown);
    }

    private static OutboxRowJpaEntity activationRow(UUID tokenId) {
        return new OutboxRowJpaEntity(
            "01H9X3F4Z9YJ7K5Q6T2R8V1N4P", AuthOutboxEventTypes.ACCOUNT_ACTIVATION_REQUESTED,
            tokenId.toString(), "{\"activationTokenId\":\"" + tokenId + "\"}",
            OutboxStatus.PENDING, 0, null, null, Instant.parse("2026-08-15T12:00:00Z"), null
        );
    }
}
