package com.menta.app.outbox;

import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.shared.outbox.OutboxStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes one eligible outbox row in an independent transaction.
 *
 * <p>This is deliberately separate from the scheduler so Spring can apply
 * {@link Transactional} through its proxy for every row.</p>
 */
@Component
public class OutboxReconciliationWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxReconciliationWorker.class);

    private final OutboxRowJpaRepository repository;
    private final List<OutboxEventHandler> handlers;
    private final Duration backoff;

    /** Creates a worker that dispatches each row through exactly one handler. */
    public OutboxReconciliationWorker(
        OutboxRowJpaRepository repository,
        List<OutboxEventHandler> handlers,
        @Value("${auth.outbox.reconcile-backoff-seconds:30}") long backoffSeconds
    ) {
        this.repository = repository;
        this.handlers = List.copyOf(handlers);
        this.backoff = Duration.ofSeconds(backoffSeconds);
    }

    /**
     * Processes one row and reports whether its side effect failed.
     *
     * @return {@code true} when the side effect failed and no heartbeat
     *         should be emitted for the tick.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean process(OutboxRowJpaEntity row) {
        Instant now = Instant.now();
        try {
            resolveHandler(row.getEventType()).handle(row);
        } catch (RuntimeException sideEffectFailure) {
            log.warn(
                "Outbox repeat-failed id={} eventType={} attempts={} cause={}",
                row.getId(), row.getEventType(), row.getAttempts(),
                sideEffectFailure.getMessage());
            row.setStatus(OutboxStatus.FAILED);
            row.setAttempts(row.getAttempts() + 1);
            row.setLastError(truncate(sideEffectFailure.getMessage(), 1000));
            row.setNextRetryAt(now.plus(backoff));
            repository.save(row);
            return true;
        }

        row.setStatus(OutboxStatus.COMPLETED);
        row.setProcessedAt(now);
        row.setLastError(null);
        row.setNextRetryAt(null);
        repository.save(row);
        return false;
    }

    private OutboxEventHandler resolveHandler(String eventType) {
        List<OutboxEventHandler> matchingHandlers = handlers.stream()
            .filter(handler -> handler.supports(eventType))
            .toList();
        if (matchingHandlers.isEmpty()) {
            throw new IllegalStateException("No handler registered for event type: " + eventType);
        }
        if (matchingHandlers.size() > 1) {
            throw new IllegalStateException(
                "Multiple handlers registered for event type: " + eventType
            );
        }
        return matchingHandlers.getFirst();
    }

    private static String truncate(String message, int max) {
        if (message == null) {
            return null;
        }
        return message.length() <= max ? message : message.substring(0, max);
    }
}
