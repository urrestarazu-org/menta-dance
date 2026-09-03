package com.menta.billing.infrastructure.scheduling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.SubscriptionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link SubscriptionExpiryReconciler} (US-BILLING-012, design A6). Mirrors
 * {@code WebhookInboxReconcilerTest}'s direct-instantiation, mocked-worker pattern exactly.
 */
class SubscriptionExpiryReconcilerTest {

    @Test
    void delegatesPerId() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        SubscriptionExpiryWorker worker = mock(SubscriptionExpiryWorker.class);
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-09-03T00:00:00Z");
        when(clock.now()).thenReturn(now);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(subscriptionRepository.findExpirableIds(eq(now), eq(100))).thenReturn(List.of(first, second));

        new SubscriptionExpiryReconciler(subscriptionRepository, worker, clock, 100).tick();

        verify(worker, times(1)).expireOne(first);
        verify(worker, times(1)).expireOne(second);
    }

    @Test
    void batchSurvivesFailure() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        SubscriptionExpiryWorker worker = mock(SubscriptionExpiryWorker.class);
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-09-03T00:00:00Z");
        when(clock.now()).thenReturn(now);
        UUID failing = UUID.randomUUID();
        UUID surviving = UUID.randomUUID();
        when(subscriptionRepository.findExpirableIds(eq(now), eq(100))).thenReturn(List.of(failing, surviving));
        doThrow(new RuntimeException("boom")).when(worker).expireOne(failing);

        new SubscriptionExpiryReconciler(subscriptionRepository, worker, clock, 100).tick();

        verify(worker, times(1)).expireOne(failing);
        verify(worker, times(1)).expireOne(surviving);
    }

    @Test
    void nothingToExpireDispatchesNothing() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        SubscriptionExpiryWorker worker = mock(SubscriptionExpiryWorker.class);
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-09-03T00:00:00Z");
        when(clock.now()).thenReturn(now);
        when(subscriptionRepository.findExpirableIds(any(Instant.class), eq(100))).thenReturn(List.of());

        new SubscriptionExpiryReconciler(subscriptionRepository, worker, clock, 100).tick();

        verify(worker, times(0)).expireOne(any());
    }
}
