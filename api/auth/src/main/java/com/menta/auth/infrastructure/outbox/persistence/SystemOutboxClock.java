package com.menta.auth.infrastructure.outbox.persistence;

import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * System-clock implementation of OutboxClock used in production wiring.
 */
@Component
public class SystemOutboxClock implements OutboxClock {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
