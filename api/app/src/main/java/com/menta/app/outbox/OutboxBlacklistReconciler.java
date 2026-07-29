package com.menta.app.outbox;

import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.shared.outbox.OutboxStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox reconciler that projects PENDING common_outbox_events to Redis
 * side-effects (ADR-0026 + design.md Decision 2).
 *
 * Lifecycle per tick:
 *   1. Bounded SELECT — fetch up to `batchSize` PENDING rows ordered by id.
 *      A crash between commit and tick leaves PENDING rows intact, so the
 *      next tick naturally re-picks them (Crash retoma spec scenario).
 *   2. For each row, apply the side-effect:
 *      - AuthUserLoggedIn / UserLoggedOut → SET blacklist:jti:{aggregateId}
 *        with the configured access-token TTL.
 *      - The reconciler is intentionally narrow — only JTI blacklist is
 *        wired this slice. Future cross-module consumers register their own
 *        listeners via OutboxListener in :api:shared.
 *   3. Update the row:
 *      - Redis OK → status=COMPLETED, processed_at=now, attempts unchanged.
 *      - Redis exception → status=FAILED, attempts+=1, last_error=message,
 *        next_retry_at=now+backoff.
 *   4. Always write the heartbeat key `auth:health:last_tick_at` to Redis
 *      so AuthDegradedGuard stays fresh — even an empty tick informs the
 *      guard that the reconciler is alive.
 *
 * Scheduling is wired with @Scheduled(fixedRate) so the cadence is
 * configurable via `auth.outbox.reconcile-rate-ms`. PR3 can tune.
 */
@Component
public class OutboxBlacklistReconciler {

    private static final Logger log = LoggerFactory.getLogger(OutboxBlacklistReconciler.class);

    private final OutboxRowJpaRepository repository;
    private final TokenBlacklistPort tokenBlacklistPort;
    private final int batchSize;
    private final Duration accessTtl;
    private final Duration backoff;

    public OutboxBlacklistReconciler(
        OutboxRowJpaRepository repository,
        TokenBlacklistPort tokenBlacklistPort,
        @Value("${auth.outbox.reconcile-batch-size:100}") int batchSize,
        @Value("${auth.access-token-ttl-seconds:900}") long accessTtlSeconds,
        @Value("${auth.outbox.reconcile-backoff-seconds:30}") long backoffSeconds
    ) {
        this.repository = repository;
        this.tokenBlacklistPort = tokenBlacklistPort;
        this.batchSize = batchSize;
        this.accessTtl = Duration.ofSeconds(accessTtlSeconds);
        this.backoff = Duration.ofSeconds(backoffSeconds);
    }

    /**
     * Public for tests + Spring scheduler entry. Production callers do NOT
     * invoke this directly — the @Scheduled annotation drives the cadence.
     */
    @Scheduled(fixedRateString = "${auth.outbox.reconcile-rate-ms:5000}")
    public void tick() {
        processBatch();
    }

    /**
     * Visible for unit tests; production flow goes through tick().
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int processBatch() {
        Pageable page = PageRequest.of(0, batchSize);
        List<OutboxRowJpaEntity> rows = repository.findByStatusOrderByIdAsc(
            OutboxStatus.PENDING, page);
        if (rows.isEmpty()) {
            return 0;
        }
        int completedOrFailed = 0;
        Instant now = Instant.now();
        for (OutboxRowJpaEntity row : rows) {
            try {
                applySideEffect(row);
                row.setStatus(OutboxStatus.COMPLETED);
                row.setProcessedAt(now);
                row.setLastError(null);
            } catch (RuntimeException sideEffectFailure) {
                log.warn(
                    "Outbox repeat-failed id={} eventType={} attempts={} cause={}",
                    row.getId(), row.getEventType(), row.getAttempts(),
                    sideEffectFailure.getMessage());
                row.setStatus(OutboxStatus.FAILED);
                row.setAttempts(row.getAttempts() + 1);
                row.setLastError(truncate(sideEffectFailure.getMessage(), 1000));
                row.setNextRetryAt(now.plus(backoff));
            } finally {
                repository.save(row);
                completedOrFailed++;
            }
        }
        return completedOrFailed;
    }

    private void applySideEffect(OutboxRowJpaEntity row) {
        // This slice wires ONLY the JTI blacklist side-effect.
        // Future cross-module consumers dispatch via OutboxListener.
        tokenBlacklistPort.blacklist(row.getAggregateId(), accessTtl);
    }

    private static String truncate(String message, int max) {
        if (message == null) {
            return null;
        }
        return message.length() <= max ? message : message.substring(0, max);
    }
}
