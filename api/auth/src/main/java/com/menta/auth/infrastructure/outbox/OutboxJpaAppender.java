package com.menta.auth.infrastructure.outbox;

import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.auth.infrastructure.outbox.persistence.OutboxClock;
import com.menta.auth.infrastructure.outbox.persistence.UlidGenerator;
import com.menta.shared.outbox.OutboxStatus;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of the {@link OutboxAppender} port (ADR-0030).
 *
 * <h2>Role in the Transactional Outbox Pattern</h2>
 * <p>
 * This is the <b>infrastructure adapter</b> that writes outbox events to
 * {@code common_outbox_events}. It implements the application-layer port,
 * keeping the use case decoupled from JPA specifics.
 * </p>
 *
 * <h2>Transaction semantics</h2>
 * <p>
 * The {@code @Transactional(propagation = REQUIRED)} annotation ensures this
 * append <em>joins</em> the caller's existing transaction. This is the core
 * guarantee of the outbox pattern: the event row and the domain mutation share
 * the same COMMIT. If the caller has no transaction, one is created (and the
 * event would commit alone, which is a bug in the caller).
 * </p>
 *
 * <h2>Idempotency</h2>
 * <p>
 * The {@code event_id} column has a UNIQUE constraint. If the same ULID is
 * appended twice (shouldn't happen with a proper generator), the database
 * rejects the duplicate. The reconciler treats constraint violations as
 * "already applied" and moves on.
 * </p>
 *
 * <h2>What happens next?</h2>
 * <p>
 * After commit, a background worker ({@code OutboxBlacklistReconciler}) polls
 * for PENDING rows, processes them, and marks them PROCESSED. This class only
 * writes; the reconciler handles reading and lifecycle transitions.
 * </p>
 *
 * @see OutboxAppender
 * @see com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity
 */
@Component
public class OutboxJpaAppender implements OutboxAppender {

    private final OutboxRowJpaRepository repository;
    private final UlidGenerator ulidGenerator;
    private final OutboxClock clock;

    public OutboxJpaAppender(
        OutboxRowJpaRepository repository,
        UlidGenerator ulidGenerator,
        OutboxClock clock
    ) {
        this.repository = repository;
        this.ulidGenerator = ulidGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void append(String eventType, String aggregateId, String payload) {
        OutboxRowJpaEntity row = new OutboxRowJpaEntity(
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
        );
        repository.save(row);
    }
}
