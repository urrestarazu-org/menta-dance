package com.menta.physical.infrastructure.scheduling;

import com.menta.physical.application.port.out.Clock;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityHoldJpaRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Housekeeping GC for {@code physical_capacity_holds} (#208, US-PHYSICAL-004b, design B5, tasks
 * Phase 9). Pure GC, not correctness: every read of the table that matters for the capacity
 * invariant already filters {@code converted_at IS NULL AND expires_at > :now} at the point of
 * read (PR3's {@code countActiveBySessionIdForUpdate}, PR4's {@code
 * PhysicalSessionJpaRepository} availability queries) — an expired-and-unconverted hold stops
 * counting against capacity with zero sweep runs. This class only stops the table from growing
 * forever by deleting:
 *
 * <ol>
 *   <li>rows that expired and were never converted ({@code converted_at IS NULL AND
 *       expires_at <= now}) — abandoned checkouts that were never released or converted;</li>
 *   <li>rows that WERE converted, long enough ago ({@code converted_at <= now - ttl}) that they
 *       are pure history nobody reads.</li>
 * </ol>
 *
 * <p>This is the <em>worker</em>: it owns the GC logic and is always present in the context, so
 * tests can call {@link #sweep()} directly instead of waiting on a schedule. The periodic cadence
 * lives in the separate {@link HoldExpiryReconciler} bean, gated by {@code
 * physical.capacity.hold.expiry.enabled} — the same worker/trigger split billing already uses for
 * {@code SubscriptionExpiryWorker}/{@code SubscriptionExpiryReconciler}, and for the same reason:
 * turning the schedule off must not take the GC behaviour with it (#172).</p>
 *
 * <p>{@code @Transactional(REQUIRES_NEW)}: each sweep runs in its own transaction, independent of
 * any caller's ambient transaction — mirrors {@code SubscriptionExpiryWorker.expireOne}.</p>
 */
@Component
public class HoldExpiryWorker {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryWorker.class);

    private final PhysicalCapacityHoldJpaRepository holdRepository;
    private final Clock clock;
    private final Duration ttl;
    private final int batchSize;

    public HoldExpiryWorker(
        PhysicalCapacityHoldJpaRepository holdRepository, Clock clock, Duration physicalCapacityHoldTtl,
        @Value("${physical.capacity.hold.expiry.batch-size:100}") int batchSize
    ) {
        this.holdRepository = holdRepository;
        this.clock = clock;
        this.ttl = physicalCapacityHoldTtl;
        this.batchSize = batchSize;
    }

    /**
     * One GC pass: up to {@code batchSize} expired-unconverted rows, then up to {@code batchSize}
     * stale-converted rows. Returns the total number of rows deleted, mainly for tests and
     * logging — callers driven by the schedule ignore it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int sweep() {
        Instant now = clock.now();
        Instant convertedCutoff = now.minus(ttl);

        int deletedExpired = holdRepository.deleteExpiredUnconverted(now, batchSize);
        int deletedConverted = holdRepository.deleteConvertedBefore(convertedCutoff, batchSize);

        int total = deletedExpired + deletedConverted;
        if (total > 0) {
            log.info(
                "Physical capacity hold sweep: deleted {} expired-unconverted, {} stale-converted",
                deletedExpired, deletedConverted
            );
        }
        return total;
    }
}
