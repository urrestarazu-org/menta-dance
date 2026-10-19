package com.menta.auth.infrastructure.time;

import com.menta.auth.application.port.out.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * System-clock implementation of the application {@link Clock} port used in
 * production wiring; delegates to {@code Instant.now()}. Mirrors
 * {@code SystemOutboxClock} for the outbox-specific concern.
 */
@Component
public class SystemClock implements Clock {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
