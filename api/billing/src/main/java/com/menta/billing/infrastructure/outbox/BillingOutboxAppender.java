package com.menta.billing.infrastructure.outbox;

import com.menta.billing.application.port.out.BillingOutboxAppenderPort;
import com.menta.shared.outbox.OutboxStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link BillingOutboxAppenderPort}.
 *
 * <p>Mirrors {@code com.menta.auth.infrastructure.outbox.OutboxJpaAppender}
 * exactly: REQUIRED propagation so the appender joins the caller's
 * transaction (no separate commit). Both appenders map to the SAME
 * {@code common_outbox_events} table — id at the row level is opaque
 * to either side; identity is structural per JPA mapping class.</p>
 */
@Component
public class BillingOutboxAppender implements BillingOutboxAppenderPort {

    private final BillingOutboxRowJpaRepository repository;
    private final BillingUlidGenerator ulidGenerator;
    private final BillingOutboxClock clock;

    public BillingOutboxAppender(
        BillingOutboxRowJpaRepository repository,
        BillingUlidGenerator ulidGenerator,
        BillingOutboxClock clock
    ) {
        this.repository = repository;
        this.ulidGenerator = ulidGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void append(String eventType, String aggregateId, String payload) {
        repository.save(new BillingOutboxRowJpaEntity(
            ulidGenerator.next(),
            eventType,
            aggregateId,
            payload,
            OutboxStatus.PENDING,
            0,
            null,
            null,
            clock.now(),
            null
        ));
    }
}
