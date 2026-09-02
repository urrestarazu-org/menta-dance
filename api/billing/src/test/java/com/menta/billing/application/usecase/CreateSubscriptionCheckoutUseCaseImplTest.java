package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.CreateSubscriptionCheckoutCommand;
import com.menta.billing.application.dto.OverlapNotice;
import com.menta.billing.application.dto.PaymentPreferenceRequest;
import com.menta.billing.application.dto.PaymentPreferenceResult;
import com.menta.billing.application.dto.SubscriptionCheckoutResult;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentPreferencePort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.exception.PaymentMethodNotAcceptedException;
import com.menta.billing.domain.exception.PaymentPreferenceUnavailableException;
import com.menta.billing.domain.exception.PlanNotAvailableException;
import com.menta.billing.domain.exception.SubscriptionAlreadyActiveException;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanCourse;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.domain.model.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateSubscriptionCheckoutUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final PlanId PLAN_ID = PlanId.generate();
    private static final Money PRICE = Money.of(new BigDecimal("15000.00"), "ARS");
    private static final String MERCHANT_ACCOUNT_ID = "merchant-1";

    private PlanRepository planRepository;
    private PaymentRepository paymentRepository;
    private SubscriptionRepository subscriptionRepository;
    private PaymentPreferencePort paymentPreferencePort;
    private Clock clock;
    private CreateSubscriptionCheckoutUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        paymentPreferencePort = mock(PaymentPreferencePort.class);
        clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRepository.saveNewCheckout(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRepository.findByUserIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(subscriptionRepository.findCurrentByUserId(any())).thenReturn(Optional.empty());
        when(subscriptionRepository.findLatestCancelledWithRemainingAccess(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(paymentPreferencePort.createPreference(any()))
            .thenReturn(new PaymentPreferenceResult("pref-1", "https://mp.example/checkout/pref-1"));
        useCase = new CreateSubscriptionCheckoutUseCaseImpl(
            planRepository, paymentRepository, subscriptionRepository, paymentPreferencePort, clock,
            MERCHANT_ACCOUNT_ID
        );
    }

    private static Plan activePlan(PaymentMethod... methods) {
        return new Plan(
            PLAN_ID, "Plan Mensual", "Acceso mensual", PRICE, 30, false, PlanStatus.ACTIVE, "T", "C",
            List.of(PlanCourse.of("course-1")), Set.of(methods)
        );
    }

    private static CreateSubscriptionCheckoutCommand command() {
        return new CreateSubscriptionCheckoutCommand(
            USER_ID, PLAN_ID.toString(), PaymentMethod.MERCADO_PAGO, "idem-1"
        );
    }

    // --- Escenario 1 ---

    @Test
    void creates_the_local_payment_and_subscription_before_asking_the_provider_for_anything() {
        when(planRepository.findActiveById(PLAN_ID)).thenReturn(Optional.of(activePlan(PaymentMethod.MERCADO_PAGO)));

        SubscriptionCheckoutResult result = useCase.create(command());

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertThat(payment.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(payment.getValue().getExpectedAmount()).isEqualTo(PRICE);
        assertThat(payment.getValue().getExpectedMerchantAccountId()).isEqualTo(MERCHANT_ACCOUNT_ID);
        assertThat(payment.getValue().getStatus()).isEqualTo(new PaymentStatus.AwaitingProvider());
        assertThat(payment.getValue().getProviderPaymentId()).isEmpty();
        assertThat(payment.getValue().getTarget()).isEqualTo(new PaymentTarget.Virtual(PLAN_ID.toString()));
        assertThat(payment.getValue().getExpectedExternalReference())
            .isEqualTo("SUB-" + payment.getValue().getId());

        ArgumentCaptor<Subscription> claimed = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).saveNewCheckout(claimed.capture());
        assertThat(claimed.getValue().getStatus()).isEqualTo(SubscriptionStatus.PENDING);
        assertThat(claimed.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(claimed.getValue().getPlanId()).isEqualTo(PLAN_ID);
        assertThat(claimed.getValue().getPaymentId()).contains(payment.getValue().getId());

        assertThat(result.status()).isEqualTo(SubscriptionStatus.PENDING);
        assertThat(result.checkoutUrl()).isEqualTo("https://mp.example/checkout/pref-1");
        assertThat(result.providerPreferenceId()).isEqualTo("pref-1");
        assertThat(result.paymentId()).isEqualTo(payment.getValue().getId().toString());
        assertThat(result.planId()).isEqualTo(PLAN_ID.toString());
    }

    /** The provider must receive OUR reference — it is what the eventual webhook correlates against. */
    @Test
    void sends_our_external_reference_and_the_plan_price_to_the_provider() {
        when(planRepository.findActiveById(PLAN_ID)).thenReturn(Optional.of(activePlan(PaymentMethod.MERCADO_PAGO)));

        SubscriptionCheckoutResult result = useCase.create(command());

        ArgumentCaptor<PaymentPreferenceRequest> request = ArgumentCaptor.forClass(PaymentPreferenceRequest.class);
        verify(paymentPreferencePort).createPreference(request.capture());
        assertThat(request.getValue().externalReference()).isEqualTo(result.externalReference());
        assertThat(request.getValue().externalReference()).isEqualTo("SUB-" + result.paymentId());
        assertThat(request.getValue().amount()).isEqualTo(PRICE);
        assertThat(request.getValue().title()).isEqualTo("Plan Mensual");
    }

    @Test
    void records_the_preference_on_the_subscription_without_confusing_it_with_a_payment_id() {
        when(planRepository.findActiveById(PLAN_ID)).thenReturn(Optional.of(activePlan(PaymentMethod.MERCADO_PAGO)));

        useCase.create(command());

        ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(saved.capture());
        assertThat(saved.getValue().getProviderPreferenceId()).contains("pref-1");
        assertThat(saved.getValue().getCheckoutUrl()).contains("https://mp.example/checkout/pref-1");
    }

    // --- Escenario 4 ---

    @Test
    void an_unknown_or_inactive_plan_is_rejected_without_any_write_or_provider_call() {
        when(planRepository.findActiveById(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.create(command()))
            .isInstanceOf(PlanNotAvailableException.class)
            .satisfies(thrown -> assertThat(((PlanNotAvailableException) thrown).getErrorCode())
                .isEqualTo("PLAN_NOT_AVAILABLE"));

        verify(paymentRepository, never()).save(any());
        verify(subscriptionRepository, never()).saveNewCheckout(any());
        verify(paymentPreferencePort, never()).createPreference(any());
    }

    // --- Escenario 4b ---

    @Test
    void a_payment_method_the_plan_does_not_accept_is_rejected_and_names_the_accepted_ones() {
        when(planRepository.findActiveById(PLAN_ID)).thenReturn(Optional.of(activePlan(PaymentMethod.BANK_TRANSFER)));

        assertThatThrownBy(() -> useCase.create(command()))
            .isInstanceOf(PaymentMethodNotAcceptedException.class)
            .satisfies(thrown -> {
                PaymentMethodNotAcceptedException rejected = (PaymentMethodNotAcceptedException) thrown;
                assertThat(rejected.getErrorCode()).isEqualTo("PAYMENT_METHOD_NOT_ACCEPTED");
                assertThat(rejected.getAcceptedPaymentMethods()).containsExactly(PaymentMethod.BANK_TRANSFER);
            });

        verify(paymentRepository, never()).save(any());
        verify(subscriptionRepository, never()).saveNewCheckout(any());
        verify(paymentPreferencePort, never()).createPreference(any());
    }

    @Test
    void bank_transfer_is_rejected_before_it_can_be_sent_to_checkout_pro() {
        CreateSubscriptionCheckoutCommand transferCommand = new CreateSubscriptionCheckoutCommand(
            USER_ID, PLAN_ID.toString(), PaymentMethod.BANK_TRANSFER, "idem-transfer"
        );

        assertThatThrownBy(() -> useCase.create(transferCommand))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Checkout Pro requires MERCADO_PAGO");

        verify(planRepository, never()).findActiveById(any());
        verify(paymentRepository, never()).save(any());
        verify(subscriptionRepository, never()).saveNewCheckout(any());
        verify(paymentPreferencePort, never()).createPreference(any());
    }

    // --- Escenario 3 ---

    @Test
    void an_active_subscription_blocks_a_new_checkout_and_reports_its_expiry() {
        Subscription active = Subscription
            .pendingCheckout(UUID.randomUUID(), PaymentId.generate(), USER_ID, PLAN_ID, "idem-0", NOW)
            .activate(NOW, 30, List.of("course-1"));
        when(planRepository.findActiveById(PLAN_ID)).thenReturn(Optional.of(activePlan(PaymentMethod.MERCADO_PAGO)));
        when(subscriptionRepository.findCurrentByUserId(USER_ID)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> useCase.create(command()))
            .isInstanceOf(SubscriptionAlreadyActiveException.class)
            .satisfies(thrown -> {
                SubscriptionAlreadyActiveException conflict = (SubscriptionAlreadyActiveException) thrown;
                assertThat(conflict.getErrorCode()).isEqualTo("SUBSCRIPTION_ALREADY_ACTIVE");
                assertThat(conflict.getCurrentEndDate()).contains(Instant.parse("2026-09-17T12:00:00Z"));
            });

        verify(paymentRepository, never()).save(any());
        verify(subscriptionRepository, never()).saveNewCheckout(any());
        verify(paymentPreferencePort, never()).createPreference(any());
    }

    @Test
    void a_pending_checkout_also_blocks_a_new_one_but_has_no_expiry_to_report() {
        Subscription pending = Subscription.pendingCheckout(
            UUID.randomUUID(), PaymentId.generate(), USER_ID, PLAN_ID, "idem-0", NOW
        );
        when(planRepository.findActiveById(PLAN_ID)).thenReturn(Optional.of(activePlan(PaymentMethod.MERCADO_PAGO)));
        when(subscriptionRepository.findCurrentByUserId(USER_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> useCase.create(command()))
            .isInstanceOf(SubscriptionAlreadyActiveException.class)
            .satisfies(thrown -> assertThat(
                ((SubscriptionAlreadyActiveException) thrown).getCurrentEndDate()
            ).isEmpty());
    }

    /**
     * The lookup above is only a friendly path. The guarantee is the unique
     * constraint the repository enforces — a race that slips past the lookup
     * still loses, and its transaction rolls back with the payment in it.
     */
    @Test
    void a_concurrent_checkout_that_loses_the_slot_constraint_never_reaches_the_provider() {
        when(planRepository.findActiveById(PLAN_ID)).thenReturn(Optional.of(activePlan(PaymentMethod.MERCADO_PAGO)));
        when(subscriptionRepository.saveNewCheckout(any()))
            .thenThrow(new SubscriptionAlreadyActiveException(null));

        assertThatThrownBy(() -> useCase.create(command()))
            .isInstanceOf(SubscriptionAlreadyActiveException.class);

        verify(paymentPreferencePort, never()).createPreference(any());
    }

    // --- Escenario 5 ---

    @Test
    void a_replayed_idempotency_key_returns_the_first_result_without_a_second_charge() {
        Subscription existing = Subscription
            .pendingCheckout(UUID.randomUUID(), PaymentId.generate(), USER_ID, PLAN_ID, "idem-1", NOW)
            .withCheckout("pref-1", "https://mp.example/checkout/pref-1");
        when(subscriptionRepository.findByUserIdAndIdempotencyKey(USER_ID, "idem-1"))
            .thenReturn(Optional.of(existing));

        SubscriptionCheckoutResult result = useCase.create(command());

        assertThat(result.subscriptionId()).isEqualTo(existing.getId().toString());
        assertThat(result.paymentId()).isEqualTo(existing.getPaymentId().orElseThrow().toString());
        assertThat(result.checkoutUrl()).isEqualTo("https://mp.example/checkout/pref-1");
        assertThat(result.externalReference()).isEqualTo("SUB-" + existing.getPaymentId().orElseThrow());
        verify(paymentPreferencePort, never()).createPreference(any());
        verify(paymentRepository, never()).save(any());
        verify(subscriptionRepository, never()).saveNewCheckout(any());
        // A replay of the request that created the current subscription is not a conflict.
        verify(subscriptionRepository, never()).findCurrentByUserId(any());
        verify(planRepository, never()).findActiveById(any());
    }

    // --- Provider failure ---

    @Test
    void a_provider_failure_aborts_the_checkout_instead_of_leaving_an_unpayable_payment() {
        when(planRepository.findActiveById(PLAN_ID)).thenReturn(Optional.of(activePlan(PaymentMethod.MERCADO_PAGO)));
        when(paymentPreferencePort.createPreference(any()))
            .thenThrow(new IllegalStateException("provider down"));

        assertThatThrownBy(() -> useCase.create(command()))
            .isInstanceOf(PaymentPreferenceUnavailableException.class)
            .satisfies(thrown -> assertThat(((PaymentPreferenceUnavailableException) thrown).getErrorCode())
                .isEqualTo("PAYMENT_PREFERENCE_UNAVAILABLE"))
            .hasRootCauseMessage("provider down");

        // No second attempt: US-BILLING-010 forbids opening another charge on an uncertain result.
        verify(paymentPreferencePort).createPreference(any());
    }

    // --- D3: overlap notice ---

    /** S5: a new checkout for the same plan the buyer already paid for, still cancelled-but-live. */
    @Test
    void a_checkout_overlapping_a_still_paid_cancellation_for_the_same_plan_carries_a_notice() {
        when(planRepository.findActiveById(PLAN_ID)).thenReturn(Optional.of(activePlan(PaymentMethod.MERCADO_PAGO)));
        Subscription cancelledWithAccess = Subscription
            .pendingCheckout(UUID.randomUUID(), PaymentId.generate(), USER_ID, PLAN_ID, "idem-0", NOW)
            .activate(NOW, 30, List.of("course-1"))
            .cancel(USER_ID, null, NOW);
        when(subscriptionRepository.findLatestCancelledWithRemainingAccess(USER_ID, PLAN_ID, NOW))
            .thenReturn(Optional.of(cancelledWithAccess));

        SubscriptionCheckoutResult result = useCase.create(command());

        assertThat(result.overlapNotice()).isEqualTo(
            new OverlapNotice("OVERLAPPING_PAID_PERIOD", cancelledWithAccess.getEndDate().orElseThrow())
        );
    }

    /** S6: no overlap-eligible cancellation for this plan — the buyer gets no notice at all. */
    @Test
    void a_checkout_with_no_overlapping_cancellation_reports_no_notice() {
        when(planRepository.findActiveById(PLAN_ID)).thenReturn(Optional.of(activePlan(PaymentMethod.MERCADO_PAGO)));

        SubscriptionCheckoutResult result = useCase.create(command());

        assertThat(result.overlapNotice()).isNull();
    }

    /**
     * S7, A9: the replay branch returns before the new-checkout path ever runs, so it must
     * compute the notice through the very same centralized {@code toResult} — never a
     * second, drifted copy of the overlap logic.
     */
    @Test
    void a_replayed_idempotency_key_still_carries_the_overlap_notice() {
        Subscription existing = Subscription
            .pendingCheckout(UUID.randomUUID(), PaymentId.generate(), USER_ID, PLAN_ID, "idem-1", NOW)
            .withCheckout("pref-1", "https://mp.example/checkout/pref-1");
        when(subscriptionRepository.findByUserIdAndIdempotencyKey(USER_ID, "idem-1"))
            .thenReturn(Optional.of(existing));
        Subscription cancelledWithAccess = Subscription
            .pendingCheckout(UUID.randomUUID(), PaymentId.generate(), USER_ID, PLAN_ID, "idem-0", NOW)
            .activate(NOW, 30, List.of("course-1"))
            .cancel(USER_ID, null, NOW);
        when(subscriptionRepository.findLatestCancelledWithRemainingAccess(USER_ID, PLAN_ID, NOW))
            .thenReturn(Optional.of(cancelledWithAccess));

        SubscriptionCheckoutResult result = useCase.create(command());

        assertThat(result.overlapNotice()).isEqualTo(
            new OverlapNotice("OVERLAPPING_PAID_PERIOD", cancelledWithAccess.getEndDate().orElseThrow())
        );
    }

    @Test
    void the_command_refuses_a_request_that_does_not_identify_a_user_plan_or_key() {
        assertThatThrownBy(() -> new CreateSubscriptionCheckoutCommand(
            null, PLAN_ID.toString(), PaymentMethod.MERCADO_PAGO, "idem-1"
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CreateSubscriptionCheckoutCommand(
            USER_ID, PLAN_ID.toString(), null, "idem-1"
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CreateSubscriptionCheckoutCommand(
            USER_ID, " ", PaymentMethod.MERCADO_PAGO, "idem-1"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreateSubscriptionCheckoutCommand(
            USER_ID, PLAN_ID.toString(), PaymentMethod.MERCADO_PAGO, " "
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
