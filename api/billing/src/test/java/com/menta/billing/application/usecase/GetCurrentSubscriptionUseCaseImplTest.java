package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.CurrentSubscriptionResult;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.exception.NoSubscriptionException;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.domain.model.SubscriptionStatus;
import com.menta.billing.domain.model.SubscriptionType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetCurrentSubscriptionUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    private SubscriptionRepository subscriptionRepository;
    private Clock clock;
    private GetCurrentSubscriptionUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        useCase = new GetCurrentSubscriptionUseCaseImpl(subscriptionRepository, clock);
    }

    private static Subscription subscription(
        UUID id, UUID userId, PlanId planId, SubscriptionStatus status, Instant startDate, Instant endDate,
        String checkoutUrl
    ) {
        PaymentId paymentId = status == SubscriptionStatus.PENDING ? PaymentId.generate() : PaymentId.generate();
        return new Subscription(
            id, paymentId, userId, planId, "idem-" + id, status, FulfillmentStatus.ASSIGNED, startDate, endDate,
            List.of("course-1"), checkoutUrl == null ? null : "pref-1", checkoutUrl, NOW.minusSeconds(86400 * 30),
            null, SubscriptionType.PAID, null
        );
    }

    @Test
    void slot_first_active_beats_an_older_expired_row_and_never_consults_the_expired_fallback() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        PlanId planId = PlanId.generate();
        Subscription active = subscription(
            subscriptionId, userId, planId, SubscriptionStatus.ACTIVE, NOW.minusSeconds(86400),
            NOW.plusSeconds(86400 * 20), null
        );
        when(subscriptionRepository.findCurrentByUserId(userId)).thenReturn(Optional.of(active));

        CurrentSubscriptionResult result = useCase.current(userId);

        assertThat(result).isInstanceOf(CurrentSubscriptionResult.Active.class);
        CurrentSubscriptionResult.Active activeResult = (CurrentSubscriptionResult.Active) result;
        assertThat(activeResult.subscriptionId()).isEqualTo(subscriptionId.toString());
        assertThat(activeResult.planId()).isEqualTo(planId.toString());
        assertThat(activeResult.daysRemaining()).isEqualTo(20L);
        assertThat(activeResult.expiringSoon()).isFalse();
        verify(subscriptionRepository, never()).findLatestExpiredByUserId(any());
    }

    @Test
    void an_active_row_within_the_expiring_soon_window_sets_the_flag() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        PlanId planId = PlanId.generate();
        Subscription active = subscription(
            subscriptionId, userId, planId, SubscriptionStatus.ACTIVE, NOW.minusSeconds(86400),
            NOW.plusSeconds(86400 * 7), null
        );
        when(subscriptionRepository.findCurrentByUserId(userId)).thenReturn(Optional.of(active));

        CurrentSubscriptionResult result = useCase.current(userId);

        CurrentSubscriptionResult.Active activeResult = (CurrentSubscriptionResult.Active) result;
        assertThat(activeResult.expiringSoon()).isTrue();
    }

    @Test
    void a_pending_row_maps_to_pending_payment_with_checkout_url_and_no_dates() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        PlanId planId = PlanId.generate();
        Subscription pending = subscription(
            subscriptionId, userId, planId, SubscriptionStatus.PENDING, null, null,
            "https://mp.example/checkout/pref-1"
        );
        when(subscriptionRepository.findCurrentByUserId(userId)).thenReturn(Optional.of(pending));

        CurrentSubscriptionResult result = useCase.current(userId);

        assertThat(result).isInstanceOf(CurrentSubscriptionResult.PendingPayment.class);
        CurrentSubscriptionResult.PendingPayment pendingResult = (CurrentSubscriptionResult.PendingPayment) result;
        assertThat(pendingResult.subscriptionId()).isEqualTo(subscriptionId.toString());
        assertThat(pendingResult.planId()).isEqualTo(planId.toString());
        assertThat(pendingResult.checkoutUrl()).isEqualTo("https://mp.example/checkout/pref-1");
        verify(subscriptionRepository, never()).findLatestExpiredByUserId(any());
    }

    @Test
    void an_empty_current_slot_falls_back_to_the_latest_expired_row() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        PlanId planId = PlanId.generate();
        Subscription expired = subscription(
            subscriptionId, userId, planId, SubscriptionStatus.EXPIRED, NOW.minusSeconds(86400 * 40),
            NOW.minusSeconds(86400 * 10), null
        );
        when(subscriptionRepository.findCurrentByUserId(userId)).thenReturn(Optional.empty());
        when(subscriptionRepository.findLatestExpiredByUserId(userId)).thenReturn(Optional.of(expired));

        CurrentSubscriptionResult result = useCase.current(userId);

        assertThat(result).isInstanceOf(CurrentSubscriptionResult.Expired.class);
        CurrentSubscriptionResult.Expired expiredResult = (CurrentSubscriptionResult.Expired) result;
        assertThat(expiredResult.subscriptionId()).isEqualTo(subscriptionId.toString());
        assertThat(expiredResult.planId()).isEqualTo(planId.toString());
        assertThat(expiredResult.startDate()).isEqualTo(expired.getStartDate().orElseThrow());
        assertThat(expiredResult.endDate()).isEqualTo(expired.getEndDate().orElseThrow());
    }

    @Test
    void neither_lookup_returning_anything_throws_no_subscription() {
        UUID userId = UUID.randomUUID();
        when(subscriptionRepository.findCurrentByUserId(userId)).thenReturn(Optional.empty());
        when(subscriptionRepository.findLatestExpiredByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.current(userId)).isInstanceOf(NoSubscriptionException.class);
    }

    /**
     * D1 regression guard: a CANCELLED-with-remaining-access row is never returned by either
     * lookup this use case calls — {@code findLatestCancelledWithRemainingAccess} is deliberately
     * unwired, so the same-empty-both-lookups path above is the observable behavior here too.
     */
    @Test
    void a_cancelled_with_remaining_access_row_is_not_returned_even_though_it_exists() {
        UUID userId = UUID.randomUUID();
        when(subscriptionRepository.findCurrentByUserId(userId)).thenReturn(Optional.empty());
        when(subscriptionRepository.findLatestExpiredByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.current(userId)).isInstanceOf(NoSubscriptionException.class);

        verify(subscriptionRepository, never()).findLatestCancelledWithRemainingAccess(any(), any(), any());
    }
}
