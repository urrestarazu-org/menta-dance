package com.menta.app.outbox;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.port.out.ActivationNotificationPort;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Dispatches activation requests to the auth module's notification port. */
@Component
public class ActivationOutboxEventHandler implements OutboxEventHandler {

    private final ActivationNotificationPort notificationPort;

    public ActivationOutboxEventHandler(ActivationNotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    @Override
    public boolean supports(String eventType) {
        return AuthOutboxEventTypes.ACCOUNT_ACTIVATION_REQUESTED.equals(eventType);
    }

    @Override
    public void handle(OutboxRowJpaEntity row) {
        notificationPort.sendActivationEmail(UUID.fromString(row.getAggregateId()));
    }
}
