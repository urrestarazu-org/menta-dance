package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.BankAccountDetails;
import com.menta.billing.application.dto.BankTransferInstructions;
import com.menta.billing.application.dto.CreateSubscriptionCheckoutCommand;
import com.menta.billing.application.dto.RateLimitDecision;
import com.menta.billing.application.dto.SubscriptionCheckoutResult;
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.exception.BankTransferRateLimitedException;
import com.menta.billing.domain.exception.PaymentMethodNotAcceptedException;
import com.menta.billing.domain.exception.PlanNotAvailableException;
import com.menta.billing.domain.exception.SubscriptionAlreadyActiveException;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class CreateBankTransferSubscriptionUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final PlanId PLAN_ID = PlanId.generate();
    private static final Money PRICE = Money.of(new BigDecimal("15000.00"), "ARS");
    private static final BankAccountDetails ACCOUNT =
        new BankAccountDetails("0000003100000000000000", "menta.dance", "Menta Dance SRL", "30-00000000-0");

    private PlanRepository planRepository;
    private PaymentRepository paymentRepository;
    private SubscriptionRepository subscriptionRepository;
    private BankTransferRateLimitPort rateLimitPort;
    private Clock clock;
    private CreateBankTransferSubscriptionUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        rateLimitPort = mock(BankTransferRateLimitPort.class);
        clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        when(rateLimitPort.consumeSubscriptionCreation(any())).thenReturn(RateLimitDecision.allowed());
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRepository.saveNewCheckout(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRepository.findCurrentByUserId(any())).thenReturn(Optional.empty());
        useCase = new CreateBankTransferSubscriptionUseCaseImpl(
            planRepository, paymentRepository, subscriptionRepository, rateLimitPort, clock, ACCOUNT
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
            USER_ID, PLAN_ID.toString(), PaymentMethod.BANK_TRANSFER, "idem-1"
        );
    }

    @Test
    void creates_the_payment_awaiting_manual_verification_and_the_pending_subscription() {
        when(planRepository.findActiveById(PLAN_ID)).thenReturn(Optional.of(activePlan(PaymentMethod.BANK_TRANSFER)));

        SubscriptionCheckoutResult result = useCase.create(command());

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertThat(payment.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(payment.getValue().getExpectedAmount()).isEqualTo(PRICE);
        assertThat(payment.getValue().getStatus()).isEqualTo(new PaymentStatus.AwaitingManualVerification());
        assertThat(payment.getValue().getProviderPaymentId()).isEmpty();
        assertThat(payment.getValue().getTarget()).isEqualTo(new PaymentTarget.Virtual(PLAN_ID.toString()));

        ArgumentCaptor<Subscription> claimed = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).saveNewCheckout(claimed.capture());
        assertThat(claimed.getValue().getStatus()).isEqualTo(SubscriptionStatus.PENDING);
        assertThat(claimed.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(claimed.getValue().getPaymentId()).contains(payment.getValue().getId());

        assertThat(result.status()).isEqualTo(SubscriptionStatus.PENDING);
        assertThat(result.checkoutUrl()).isNull();
        assertThat(result.providerPreferenceId()).isNull();
        assertThat(result.overlapNotice()).isNull();
        assertThat(result.bankTransferInstructions()).isEqualTo(new BankTransferInstructions(
            ACCOUNT.cbu(), ACCOUNT.alias(), ACCOUNT.holder(), ACCOUNT.cuit(), PRICE, result.externalReference()
        ));
    }

    /** Design C2: the reference reuses {@code CreateSubscriptionCheckoutUseCaseImpl}'s exact prefix. */
    @Test
    void the_reference_is_SUB_dash_paymentId() {
        when(planRepository.findActiveById(PLAN_ID)).thenReturn(Optional.of(activePlan(PaymentMethod.BANK_TRANSFER)));

        SubscriptionCheckoutResult result = useCase.create(command());

        assertThat(result.externalReference()).isEqualTo("SUB-" + result.paymentId());
    }

    /**
     * Design C2: a stray Mercado Pago webhook can never match a CBU, so a mismatched payment
     * routes to {@code ReconciliationRequired} instead of completing on the wrong rail.
     */
    @Test
    void the_expected_merchant_account_id_is_the_configured_cbu_not_a_provider_id() {
        when(planRepository.findActiveById(PLAN_ID)).thenReturn(Optional.of(activePlan(PaymentMethod.BANK_TRANSFER)));

        useCase.create(command());

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertThat(payment.getValue().getExpectedMerchantAccountId()).isEqualTo(ACCOUNT.cbu());
    }

    /** Design C7: attempting a checkout is itself the cost the budget bounds. */
    @Test
    void the_limiter_is_consulted_before_any_repository_write() {
        when(planRepository.findActiveById(PLAN_ID)).thenReturn(Optional.of(activePlan(PaymentMethod.BANK_TRANSFER)));

        useCase.create(command());

        InOrder order = inOrder(rateLimitPort, paymentRepository, subscriptionRepository);
        order.verify(rateLimitPort).consumeSubscriptionCreation(USER_ID);
        order.verify(paymentRepository).save(any());
        order.verify(subscriptionRepository).saveNewCheckout(any());
    }

    /** Design C7 / task 2.8: a 429 creates neither the Payment nor the Subscription. */
    @Test
    void an_exhausted_daily_budget_creates_neither_payment_nor_subscription() {
        when(rateLimitPort.consumeSubscriptionCreation(USER_ID)).thenReturn(RateLimitDecision.limited(Duration.ofHours(2)));

        assertThatThrownBy(() -> useCase.create(command()))
            .isInstanceOf(BankTransferRateLimitedException.class)
            .satisfies(thrown -> assertThat(((BankTransferRateLimitedException) thrown).getRetryAfter())
                .isEqualTo(Duration.ofHours(2)));

        verify(planRepository, never()).findActiveById(any());
        verify(paymentRepository, never()).save(any());
        verify(subscriptionRepository, never()).saveNewCheckout(any());
    }

    @Test
    void an_unknown_or_inactive_plan_is_rejected_without_any_write() {
        when(planRepository.findActiveById(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.create(command())).isInstanceOf(PlanNotAvailableException.class);

        verify(paymentRepository, never()).save(any());
        verify(subscriptionRepository, never()).saveNewCheckout(any());
    }

    @Test
    void a_plan_that_does_not_accept_bank_transfer_is_rejected() {
        when(planRepository.findActiveById(PLAN_ID)).thenReturn(Optional.of(activePlan(PaymentMethod.MERCADO_PAGO)));

        assertThatThrownBy(() -> useCase.create(command())).isInstanceOf(PaymentMethodNotAcceptedException.class);

        verify(paymentRepository, never()).save(any());
        verify(subscriptionRepository, never()).saveNewCheckout(any());
    }

    @Test
    void an_already_active_subscription_blocks_a_new_bank_transfer_checkout() {
        Subscription active = Subscription
            .pendingCheckout(UUID.randomUUID(), com.menta.billing.domain.model.PaymentId.generate(), USER_ID, PLAN_ID, "idem-0", NOW)
            .activate(NOW, 30, List.of("course-1"));
        when(planRepository.findActiveById(PLAN_ID)).thenReturn(Optional.of(activePlan(PaymentMethod.BANK_TRANSFER)));
        when(subscriptionRepository.findCurrentByUserId(USER_ID)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> useCase.create(command())).isInstanceOf(SubscriptionAlreadyActiveException.class);

        verify(paymentRepository, never()).save(any());
        verify(subscriptionRepository, never()).saveNewCheckout(any());
    }
}
