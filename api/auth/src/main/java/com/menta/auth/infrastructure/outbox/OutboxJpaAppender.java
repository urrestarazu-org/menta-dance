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
 * JPA implementation of OutboxAppender port (ADR-0027 post-commit pattern).
 *
 * Appends a row to common_outbox_events with status=PENDING and a fresh
 * ULID event_id. The transaction is REQUIRED so the append shares COMMIT
 * with the caller's domain mutation (refresh insert, user bump, etc).
 *
 * UNIQUE constraint violations on event_id and (aggregate_id, event_type)
 * propagate transparently. Idempotency policy lives at the caller / the
 * reconciler (it treats the duplicate as already-applied and moves on).
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
