package com.menta.physical.infrastructure.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The periodic trigger for {@link HoldExpiryWorker} (#208, US-PHYSICAL-004b, design B5, tasks
 * Phase 9), split out so the schedule can be turned off without taking the GC logic itself with
 * it. Mirrors billing's {@code SubscriptionExpiryReconciler} pair and the outbox module's {@code
 * OutboxBlacklistReconcilerTrigger}.
 *
 * <p>{@code @ConditionalOnProperty} sits on this CLASS, not on {@link #tick()} — Spring only
 * evaluates that condition on a component/configuration class or a {@code @Bean} method; on a
 * plain method it is inert and the job would run unconditionally (#172's lesson, already learned
 * the hard way once in this codebase and not to be relearned here). {@code
 * physical.capacity.hold.expiry.rate-ms} only controls the interval; {@code
 * physical.capacity.hold.expiry.enabled} is the real off switch.</p>
 *
 * <p>Keeping {@link HoldExpiryWorker} unconditional (no {@code @ConditionalOnProperty} on it) is
 * the other half of the fix: tests need to drive a deterministic sweep by hand via {@code
 * worker.sweep()}, and would fail to start if disabling the schedule also removed the worker
 * bean.</p>
 */
@Component
@ConditionalOnProperty(
    name = "physical.capacity.hold.expiry.enabled", havingValue = "true", matchIfMissing = true
)
public class HoldExpiryReconciler {

    private final HoldExpiryWorker worker;

    public HoldExpiryReconciler(HoldExpiryWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedRateString = "${physical.capacity.hold.expiry.rate-ms:60000}")
    public void tick() {
        worker.sweep();
    }
}
