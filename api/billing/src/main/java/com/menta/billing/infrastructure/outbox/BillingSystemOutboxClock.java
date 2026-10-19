package com.menta.billing.infrastructure.outbox;

import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Production {@link BillingOutboxClock}: delegates to {@link Instant#now()}.
 * Discovered via component scan; tests substitute their own mock.
 */
@Component
public class BillingSystemOutboxClock implements BillingOutboxClock {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
