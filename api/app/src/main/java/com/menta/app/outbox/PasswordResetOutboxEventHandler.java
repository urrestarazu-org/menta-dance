package com.menta.app.outbox;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.port.out.PasswordResetNotificationPort;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Dispatches password-reset requests to the auth module's notification port. */
@Component
public class PasswordResetOutboxEventHandler implements OutboxEventHandler {

    private final PasswordResetNotificationPort notificationPort;

    public PasswordResetOutboxEventHandler(PasswordResetNotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    @Override
    public boolean supports(String eventType) {
        return AuthOutboxEventTypes.PASSWORD_RESET_REQUESTED.equals(eventType);
    }

    @Override
    public void handle(OutboxRowJpaEntity row) {
        notificationPort.sendPasswordResetEmail(UUID.fromString(row.getAggregateId()));
    }
}
