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
 *
 * <h2>Poison rows (#672)</h2>
 *
 * <p>A row that keeps failing is retried with a fixed backoff and, once its attempts reach
 * {@code auth.outbox.max-attempts}, moves to the terminal {@link OutboxStatus#DEAD} state, which the
 * reconciler query never selects again. That includes rows of event types that no longer have a
 * consumer: {@code auth.AuthUserLoggedIn} and {@code auth.RefreshRotated} were persisted on every
 * login and refresh without any handler until they were removed, and rows already stored by older
 * builds drain to DEAD through this same cap on their first retry. DEAD rows are never deleted by
 * the retention job; requeue one after fixing the cause by setting it back to {@code PENDING} with
 * {@code attempts = 0}.</p>
 *
 * <p>A DEAD row does not leave revoked access tokens open: the {@code tokenVersion} projection only
 * protects tokens issued before the revocation, and those expire after
 * {@code auth.access-token-ttl-seconds}. Hence a floor for the cap: {@code auth.outbox.max-attempts}
 * should never be lower than {@code auth.access-token-ttl-seconds / auth.outbox.reconcile-backoff-seconds}
 * (900 / 30 = 30 with the defaults), otherwise a revocation row could turn DEAD while a token it
 * should have closed is still valid. See ADR-0030.</p>
 */
@Component
public class OutboxReconciliationWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxReconciliationWorker.class);

    /**
     * Fixed prefix of the ERROR line written when a row turns {@link OutboxStatus#DEAD}. The Grafana
     * rule {@code auth-outbox-dead.yml} matches on it ({@code |=}), so changing it silently disables
     * the alert.
     */
    public static final String DEAD_ALARM_MARKER = "alarm=auth_outbox_dead";

    private final OutboxRowJpaRepository repository;
    private final List<OutboxEventHandler> handlers;
    private final Duration backoff;
    private final int maxAttempts;

    /** Creates a worker that dispatches each row through exactly one handler. */
    public OutboxReconciliationWorker(
        OutboxRowJpaRepository repository,
        List<OutboxEventHandler> handlers,
        @Value("${auth.outbox.reconcile-backoff-seconds:30}") long backoffSeconds,
        @Value("${auth.outbox.max-attempts:100}") int maxAttempts
    ) {
        this.repository = repository;
        this.handlers = List.copyOf(handlers);
        this.backoff = Duration.ofSeconds(backoffSeconds);
        this.maxAttempts = maxAttempts;
    }

    /**
     * Processes one row and reports how it ended.
     *
     * @return the outcome; {@link OutboxProcessResult#blocksHeartbeat()} tells the reconciler whether
     *         the tick may still write its heartbeat.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OutboxProcessResult process(OutboxRowJpaEntity row) {
        Instant now = Instant.now();
        try {
            resolveHandler(row.getEventType()).handle(row);
        } catch (OutboxDispatchException dispatchFailure) {
            recordFailure(row, dispatchFailure, now);
            return OutboxProcessResult.DISPATCH_FAILED;
        } catch (RuntimeException sideEffectFailure) {
            recordFailure(row, sideEffectFailure, now);
            return OutboxProcessResult.SIDE_EFFECT_FAILED;
        }

        row.setStatus(OutboxStatus.COMPLETED);
        row.setProcessedAt(now);
        row.setLastError(null);
        row.setNextRetryAt(null);
        repository.save(row);
        return OutboxProcessResult.COMPLETED;
    }

    private void recordFailure(OutboxRowJpaEntity row, RuntimeException failure, Instant now) {
        int attempts = row.getAttempts() + 1;
        row.setAttempts(attempts);
        row.setLastError(truncate(failure.getMessage(), 1000));
        if (attempts >= maxAttempts) {
            row.setStatus(OutboxStatus.DEAD);
            row.setNextRetryAt(null);
            log.error(
                "{} id={} eventType={} attempts={} cause={}",
                DEAD_ALARM_MARKER, row.getId(), row.getEventType(), attempts,
                failure.getMessage());
        } else {
            row.setStatus(OutboxStatus.FAILED);
            row.setNextRetryAt(now.plus(backoff));
            log.warn(
                "Outbox repeat-failed id={} eventType={} attempts={} cause={}",
                row.getId(), row.getEventType(), attempts, failure.getMessage());
        }
        repository.save(row);
    }

    private OutboxEventHandler resolveHandler(String eventType) {
        List<OutboxEventHandler> matchingHandlers = handlers.stream()
            .filter(handler -> handler.supports(eventType))
            .toList();
        if (matchingHandlers.isEmpty()) {
            throw new OutboxDispatchException("No handler registered for event type: " + eventType);
        }
        if (matchingHandlers.size() > 1) {
            throw new OutboxDispatchException(
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
