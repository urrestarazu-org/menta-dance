package com.menta.physical.infrastructure.scheduling;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link HoldExpiryReconciler} (#208, design B5, tasks Phase 9) — mirrors {@code
 * SubscriptionExpiryReconcilerTest}'s direct-instantiation, mocked-worker pattern. {@code
 * @ConditionalOnProperty} gating is a Spring-context concern, not exercised here; the property
 * name and its placement on the class (not on {@link HoldExpiryReconciler#tick()}) are the fix
 * for #172 and are verified by inspection, the same way the sibling {@code
 * OutboxBlacklistReconcilerTrigger} documents it.
 */
class HoldExpiryReconcilerTest {

    @Test
    void tick_delegates_to_the_worker() {
        HoldExpiryWorker worker = mock(HoldExpiryWorker.class);
        HoldExpiryReconciler reconciler = new HoldExpiryReconciler(worker);

        reconciler.tick();

        verify(worker, times(1)).sweep();
    }
}
