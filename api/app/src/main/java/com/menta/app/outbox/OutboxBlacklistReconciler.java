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
 * Lifecycle per tick (PR3):
 *   1. Bounded SELECT — fetch up to `batchSize` rows ordered by id:
 *      - All PENDING rows (never processed)
 *      - FAILED rows whose next_retry_at <= now (due for retry)
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
 *   4. Write heartbeat key `auth:health:last_tick_at` to Redis ONLY when
 *      no Redis errors occurred in this tick. This ensures AuthDegradedGuard
 *      sees degraded state when Redis is actually down.
 *
 * Scheduling is wired with @Scheduled(fixedRate) so the cadence is
 * configurable via `auth.outbox.reconcile-rate-ms`. PR3 can tune.
 */
@Component
public class OutboxBlacklistReconciler {

    private static final Logger log = LoggerFactory.getLogger(OutboxBlacklistReconciler.class);

    private final OutboxRowJpaRepository repository;
    private final TokenBlacklistPort tokenBlacklistPort;
    private final OutboxReconciliationWorker worker;
    private final int batchSize;
    private final int retentionDays;

    public OutboxBlacklistReconciler(
        OutboxRowJpaRepository repository,
        TokenBlacklistPort tokenBlacklistPort,
        OutboxReconciliationWorker worker,
        @Value("${auth.outbox.reconcile-batch-size:100}") int batchSize,
        @Value("${auth.outbox.retention-days:7}") int retentionDays
    ) {
        this.repository = repository;
        this.tokenBlacklistPort = tokenBlacklistPort;
        this.worker = worker;
        this.batchSize = batchSize;
        this.retentionDays = retentionDays;
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
    public int processBatch() {
        Instant now = Instant.now();
        Pageable page = PageRequest.of(0, batchSize);
        // PR3: Select PENDING + (FAILED where next_retry_at <= now)
        List<OutboxRowJpaEntity> rows = repository.findPendingOrDueFailedOrderByIdAsc(now, page);
        if (rows.isEmpty()) {
            // Empty tick: write heartbeat to signal reconciler is alive
            tokenBlacklistPort.writeHeartbeat();
            return 0;
        }
        int completedOrFailed = 0;
        boolean hadRedisFailure = false;
        for (var row : rows) {
            hadRedisFailure |= worker.process(row);
            completedOrFailed++;
        }
        // PR3: Write heartbeat ONLY when no Redis failures occurred.
        // If Redis is down, writing heartbeat would fail or give false signal.
        if (!hadRedisFailure) {
            tokenBlacklistPort.writeHeartbeat();
        }
        return completedOrFailed;
    }

    // ---- Retention cleanup ----

    /**
     * Daily cleanup job to prevent unbounded table growth (ADR-0030).
     *
     * Deletes COMPLETED outbox events older than the configured retention period.
     * Runs once per day at 3:00 AM server time. The cron expression is configurable
     * via {@code auth.outbox.cleanup-cron}.
     *
     * <p>FAILED events are intentionally kept for manual inspection. A separate
     * alerting mechanism should notify operators when FAILED count grows.</p>
     */
    @Scheduled(cron = "${auth.outbox.cleanup-cron:0 0 3 * * *}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanupProcessedEvents() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        long deleted = repository.deleteByStatusAndProcessedAtBefore(
            OutboxStatus.COMPLETED, cutoff);
        if (deleted > 0) {
            log.info("Outbox cleanup: deleted {} COMPLETED events older than {} days",
                deleted, retentionDays);
        }
    }
}
