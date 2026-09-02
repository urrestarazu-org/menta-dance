package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.AssignTrialCommand;
import com.menta.billing.application.dto.TrialAssignmentResult;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.exception.PlanNotAvailableException;
import com.menta.billing.domain.exception.SubscriptionAlreadyActiveException;
import com.menta.billing.domain.exception.UserNotFoundException;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.domain.model.SubscriptionStatus;
import com.menta.billing.domain.model.SubscriptionType;
import com.menta.shared.auth.UserExistencePort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AssignTrialSubscriptionUseCaseImpl} (US-BILLING-012, Phase 3).
 *
 * <p>Validation order is what design.md A5 asserts, not incidental: admin guard → user exists
 * (404, D8) → plan available (422) → slot occupied (409). Each negative test locks one step of
 * that order; {@link #unknown_user_id_wins_over_an_inactive_plan()} proves the order directly by
 * combining two failing conditions at once.</p>
 */
class AssignTrialSubscriptionUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    private SubscriptionRepository subscriptionRepository;
    private PlanRepository planRepository;
    private UserExistencePort userExistencePort;
    private Clock clock;
    private AssignTrialSubscriptionUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        planRepository = mock(PlanRepository.class);
        userExistencePort = mock(UserExistencePort.class);
        clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        useCase = new AssignTrialSubscriptionUseCaseImpl(subscriptionRepository, planRepository, userExistencePort, clock);
    }

    private static AssignTrialCommand command(UUID userId, UUID planId, UUID adminId, boolean isAdmin, int days) {
        return new AssignTrialCommand(userId, planId.toString(), adminId, isAdmin, "evaluación de catálogo", days);
    }

    private static Plan plan(PlanId planId, int durationDays) {
        return new Plan(
            planId, "Plan Mensual", "Acceso mensual", Money.of(new BigDecimal("100.00"), "ARS"), durationDays, false,
            PlanStatus.ACTIVE, "Términos", "Política de cancelación", List.of(), Set.of(PaymentMethod.MERCADO_PAGO)
        );
    }

    private static Subscription activeSubscription(UUID userId, PlanId planId, SubscriptionStatus status) {
        return new Subscription(
            UUID.randomUUID(), PaymentId.generate(), userId, planId, "idem-1", status, FulfillmentStatus.ASSIGNED,
            NOW.minusSeconds(86400), NOW.plusSeconds(86400 * 10), List.of("course-1"), "pref-1",
            "https://mp.example/checkout/pref-1", NOW.minusSeconds(86400 * 2), null, SubscriptionType.PAID, null
        );
    }

    // --- Admin guard (design A4) -----------------------------------------------------------

    @Test
    void a_non_admin_caller_is_rejected_with_the_same_shape_as_an_unknown_user_without_touching_any_repository() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PlanId planId = PlanId.generate();

        assertThatThrownBy(() -> useCase.assign(command(userId, planId.getValue(), adminId, false, 10)))
            .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(userExistencePort);
        verifyNoInteractions(planRepository);
        verifyNoInteractions(subscriptionRepository);
    }

    // --- Unknown user (404, D8) -------------------------------------------------------------

    @Test
    void an_unknown_user_id_is_rejected_before_any_plan_or_subscription_lookup() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PlanId planId = PlanId.generate();
        when(userExistencePort.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> useCase.assign(command(userId, planId.getValue(), adminId, true, 10)))
            .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(planRepository);
        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void unknown_user_id_wins_over_an_inactive_plan() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        when(userExistencePort.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> useCase.assign(command(userId, planId, adminId, true, 10)))
            .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(planRepository);
        verifyNoInteractions(subscriptionRepository);
    }

    // --- Plan not available (422) -----------------------------------------------------------

    @Test
    void an_unavailable_plan_is_rejected_after_the_user_is_confirmed_to_exist() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PlanId planId = PlanId.generate();
        when(userExistencePort.existsById(userId)).thenReturn(true);
        when(planRepository.findActiveById(planId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.assign(command(userId, planId.getValue(), adminId, true, 10)))
            .isInstanceOf(PlanNotAvailableException.class);

        verifyNoInteractions(subscriptionRepository);
    }

    // --- Slot already occupied (409) --------------------------------------------------------

    @Test
    void a_target_user_with_a_subscription_already_in_force_is_rejected_regardless_of_its_type() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PlanId planId = PlanId.generate();
        when(userExistencePort.existsById(userId)).thenReturn(true);
        when(planRepository.findActiveById(planId)).thenReturn(Optional.of(plan(planId, 30)));
        when(subscriptionRepository.findCurrentByUserId(userId))
            .thenReturn(Optional.of(activeSubscription(userId, planId, SubscriptionStatus.ACTIVE)));

        assertThatThrownBy(() -> useCase.assign(command(userId, planId.getValue(), adminId, true, 10)))
            .isInstanceOf(SubscriptionAlreadyActiveException.class);

        verify(subscriptionRepository, never()).saveNewSubscription(any());
    }

    @Test
    void a_target_user_with_a_pending_trial_subscription_is_also_rejected() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PlanId planId = PlanId.generate();
        when(userExistencePort.existsById(userId)).thenReturn(true);
        when(planRepository.findActiveById(planId)).thenReturn(Optional.of(plan(planId, 30)));
        Subscription pendingOther = new Subscription(
            UUID.randomUUID(), null, userId, planId, "trial:other", SubscriptionStatus.PENDING,
            FulfillmentStatus.PENDING_FULFILLMENT, null, null, List.of(), null, null, NOW.minusSeconds(60), null,
            SubscriptionType.TRIAL, new com.menta.billing.domain.model.TrialGrant(NOW.minusSeconds(60), adminId, "otro motivo", 5)
        );
        when(subscriptionRepository.findCurrentByUserId(userId)).thenReturn(Optional.of(pendingOther));

        assertThatThrownBy(() -> useCase.assign(command(userId, planId.getValue(), adminId, true, 10)))
            .isInstanceOf(SubscriptionAlreadyActiveException.class);

        verify(subscriptionRepository, never()).saveNewSubscription(any());
    }

    // --- Happy path --------------------------------------------------------------------------

    @Test
    void grants_a_trial_using_the_requested_days_never_the_plan_duration_and_persists_via_save_new_subscription() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PlanId planId = PlanId.generate();
        int requestedDays = 10;
        Plan availablePlan = plan(planId, 30); // durationDays differs from requestedDays on purpose
        when(userExistencePort.existsById(userId)).thenReturn(true);
        when(planRepository.findActiveById(planId)).thenReturn(Optional.of(availablePlan));
        when(subscriptionRepository.findCurrentByUserId(userId)).thenReturn(Optional.empty());
        when(subscriptionRepository.saveNewSubscription(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TrialAssignmentResult result =
            useCase.assign(command(userId, planId.getValue(), adminId, true, requestedDays));

        assertThat(result.type()).isEqualTo(SubscriptionType.TRIAL);
        assertThat(result.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.userId()).isEqualTo(userId.toString());
        assertThat(result.planId()).isEqualTo(planId.toString());
        assertThat(result.days()).isEqualTo(requestedDays);
        assertThat(result.startDate()).isEqualTo(NOW);
        assertThat(result.endDate()).isEqualTo(NOW.plusSeconds(86400L * requestedDays));
        verify(subscriptionRepository, never()).saveNewCheckout(any());
    }
}
