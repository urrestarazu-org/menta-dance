package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.CancelSubscriptionCommand;
import com.menta.billing.application.dto.CancellationResult;
import com.menta.billing.application.dto.CancellationTarget;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.exception.SubscriptionNotFoundException;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanCourse;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.domain.model.SubscriptionStatus;
import com.menta.billing.domain.model.SubscriptionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CancelSubscriptionUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    private SubscriptionRepository subscriptionRepository;
    private PlanRepository planRepository;
    private Clock clock;
    private CancelSubscriptionUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        planRepository = mock(PlanRepository.class);
        clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        useCase = new CancelSubscriptionUseCaseImpl(subscriptionRepository, planRepository, clock);
    }

    private static Subscription activeSubscription(UUID id, UUID userId, PlanId planId) {
        return new Subscription(
            id, PaymentId.generate(), userId, planId, "idem-1", SubscriptionStatus.ACTIVE,
            FulfillmentStatus.ASSIGNED, NOW.minusSeconds(86400), NOW.plusSeconds(86400 * 10), List.of("course-1"),
            "pref-1", "https://mp.example/checkout/pref-1", NOW.minusSeconds(86400 * 2), null,
            SubscriptionType.PAID, null
        );
    }

    private static Plan plan(PlanId planId, String cancellationPolicy) {
        return new Plan(
            planId, "Plan Mensual", "Acceso mensual", Money.of(new BigDecimal("100.00"), "ARS"), 30, false,
            PlanStatus.ACTIVE, "Términos", cancellationPolicy, List.of(), Set.of(PaymentMethod.MERCADO_PAGO)
        );
    }

    // --- Own target -----------------------------------------------------------

    @Test
    void own_target_resolves_by_acting_user_id_and_cancels_it() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        PlanId planId = PlanId.generate();
        Subscription subscription = activeSubscription(subscriptionId, userId, planId);
        when(subscriptionRepository.findActiveByUserId(userId)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan(planId, "Política de cancelación")));

        CancellationResult result = useCase.cancel(
            new CancelSubscriptionCommand(new CancellationTarget.Own(), userId, false, null)
        );

        assertThat(result.subscriptionId()).isEqualTo(subscriptionId.toString());
        assertThat(result.status()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(result.accessEndsAt()).isEqualTo(subscription.getEndDate().orElseThrow());
        assertThat(result.cancellationPolicy()).isEqualTo("Política de cancelación");
        verify(planRepository).findById(planId);
        verify(planRepository, never()).findActiveById(any());
    }

    @Test
    void own_target_with_no_active_subscription_throws_subscription_not_found() {
        UUID userId = UUID.randomUUID();
        when(subscriptionRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.cancel(
            new CancelSubscriptionCommand(new CancellationTarget.Own(), userId, false, null)
        )).isInstanceOf(SubscriptionNotFoundException.class);

        verify(subscriptionRepository, never()).save(any());
    }

    // --- ById target (admin route) ---------------------------------------------

    @Test
    void by_id_target_from_an_admin_resolves_and_cancels_an_active_subscription() {
        UUID adminId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        PlanId planId = PlanId.generate();
        Subscription subscription = activeSubscription(subscriptionId, ownerId, planId);
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan(planId, "Política de cancelación")));

        CancellationResult result = useCase.cancel(new CancelSubscriptionCommand(
            new CancellationTarget.ById(subscriptionId), adminId, true, "cliente lo solicitó por soporte"
        ));

        assertThat(result.status()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void by_id_target_from_a_non_admin_throws_subscription_not_found_without_touching_the_repository() {
        UUID actingUserId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.cancel(new CancelSubscriptionCommand(
            new CancellationTarget.ById(subscriptionId), actingUserId, false, "un motivo"
        ))).isInstanceOf(SubscriptionNotFoundException.class);

        verify(subscriptionRepository, never()).findById(any());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void by_id_target_for_a_missing_subscription_throws_subscription_not_found() {
        UUID adminId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.cancel(new CancelSubscriptionCommand(
            new CancellationTarget.ById(subscriptionId), adminId, true, "un motivo"
        ))).isInstanceOf(SubscriptionNotFoundException.class);

        verify(subscriptionRepository, never()).save(any());
    }

    /** Design.md A5: not-ACTIVE reads as absent too, same as a non-owner would. */
    @Test
    void by_id_target_for_a_non_active_subscription_throws_subscription_not_found() {
        UUID adminId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        PlanId planId = PlanId.generate();
        Subscription cancelled = new Subscription(
            subscriptionId, PaymentId.generate(), ownerId, planId, "idem-1", SubscriptionStatus.CANCELLED,
            FulfillmentStatus.ASSIGNED, NOW.minusSeconds(86400), NOW.plusSeconds(86400), List.of(), null, null,
            NOW.minusSeconds(86400 * 2), null, SubscriptionType.PAID, null
        );
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> useCase.cancel(new CancelSubscriptionCommand(
            new CancellationTarget.ById(subscriptionId), adminId, true, "un motivo"
        ))).isInstanceOf(SubscriptionNotFoundException.class);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void by_id_target_with_a_blank_reason_is_rejected_before_any_save() {
        UUID adminId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.cancel(new CancelSubscriptionCommand(
            new CancellationTarget.ById(subscriptionId), adminId, true, "   "
        ))).isInstanceOf(IllegalArgumentException.class);

        verify(subscriptionRepository, never()).findById(any());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void by_id_target_with_an_absent_reason_is_rejected_before_any_save() {
        UUID adminId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.cancel(new CancelSubscriptionCommand(
            new CancellationTarget.ById(subscriptionId), adminId, true, null
        ))).isInstanceOf(IllegalArgumentException.class);

        verify(subscriptionRepository, never()).findById(any());
        verify(subscriptionRepository, never()).save(any());
    }
}
