package com.menta.billing.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.domain.model.SubscriptionStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link SubscriptionExpiryWorker} (US-BILLING-012, design A6): a real
 * transition triggers exactly one {@code save}, a no-op transition triggers none.
 */
class SubscriptionExpiryWorkerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    void savesTheExpiredSubscriptionOnARealTransition() {
        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-09-03T00:00:00Z");
        when(clock.now()).thenReturn(now);

        Subscription stale = Subscription.pendingCheckout(
            UUID.randomUUID(), PaymentId.generate(), USER_ID, PlanId.generate(), "idem-1",
            now.minus(40, ChronoUnit.DAYS)
        ).activate(now.minus(40, ChronoUnit.DAYS), 30, List.of("course-1"));
        UUID subscriptionId = stale.getId();
        when(repository.findById(subscriptionId)).thenReturn(Optional.of(stale));

        new SubscriptionExpiryWorker(repository, clock).expireOne(subscriptionId);

        verify(repository, times(1)).save(any(Subscription.class));
    }

    @Test
    void skipsSaveOnNoop() {
        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-09-03T00:00:00Z");
        when(clock.now()).thenReturn(now);

        Subscription pending = Subscription.pendingCheckout(
            UUID.randomUUID(), PaymentId.generate(), USER_ID, PlanId.generate(), "idem-1",
            now.minus(40, ChronoUnit.DAYS)
        );
        UUID subscriptionId = pending.getId();
        when(repository.findById(subscriptionId)).thenReturn(Optional.of(pending));

        new SubscriptionExpiryWorker(repository, clock).expireOne(subscriptionId);

        verify(repository, never()).save(any(Subscription.class));
    }

    @Test
    void doesNothingWhenTheRowDisappearedBeforeThisTransaction() {
        SubscriptionRepository repository = mock(SubscriptionRepository.class);
        Clock clock = mock(Clock.class);
        UUID subscriptionId = UUID.randomUUID();
        when(repository.findById(subscriptionId)).thenReturn(Optional.empty());

        new SubscriptionExpiryWorker(repository, clock).expireOne(subscriptionId);

        verify(repository, never()).save(any(Subscription.class));
    }
}
