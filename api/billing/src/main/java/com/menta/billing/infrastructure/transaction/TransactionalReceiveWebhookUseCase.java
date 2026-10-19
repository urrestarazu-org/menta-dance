package com.menta.billing.infrastructure.transaction;

import com.menta.billing.application.dto.WebhookSignal;
import com.menta.billing.application.port.in.ReceiveWebhookUseCase;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for {@link ReceiveWebhookUseCase} — the inbox
 * append must commit atomically with nothing else racing it (US-BILLING-002:
 * "responde 200 OK sólo tras comprobar ese inbox durable").
 */
public class TransactionalReceiveWebhookUseCase implements ReceiveWebhookUseCase {

    private final ReceiveWebhookUseCase delegate;

    public TransactionalReceiveWebhookUseCase(ReceiveWebhookUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public void receive(WebhookSignal signal) {
        delegate.receive(signal);
    }
}
