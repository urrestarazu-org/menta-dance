package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.ProviderPaymentResult;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.model.FulfillmentStatus;
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

class PaymentVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final Money AMOUNT = Money.of(BigDecimal.TEN, "ARS");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final PlanId PLAN_ID = PlanId.generate();

    private PaymentRepository paymentRepository;
    private PaymentProviderPort paymentProviderPort;
    private SubscriptionRepository subscriptionRepository;
    private PlanRepository planRepository;
    private Clock clock;
    private PaymentVerificationService service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        paymentProviderPort = mock(PaymentProviderPort.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        planRepository = mock(PlanRepository.class);
        clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new PaymentVerificationService(
            paymentRepository, paymentProviderPort, subscriptionRepository, planRepository, clock
        );
    }

    private static Payment boundPhysicalPayment(PaymentStatus status) {
        return new Payment(
            PaymentId.generate(), USER_ID, "mp-1", AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Physical("session-1"), status, NOW
        );
    }

    private static Payment boundVirtualPayment(PaymentStatus status) {
        return new Payment(
            PaymentId.generate(), USER_ID, "mp-1", AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Virtual(PLAN_ID.toString()), status, NOW
        );
    }

    private static Payment unboundVirtualPayment() {
        return Payment.awaitingProvider(
            PaymentId.generate(), USER_ID, AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Virtual(PLAN_ID.toString()), NOW
        );
    }

    private static ProviderPaymentResult approved() {
        return new ProviderPaymentResult("approved", AMOUNT, "ext-1", "merchant-1");
    }

    private static Plan plan(String... courseIds) {
        return new Plan(
            PLAN_ID, "Plan Mensual", "Desc", AMOUNT, 30, false, PlanStatus.ACTIVE, "T", "C",
            List.of(courseIds).stream().map(PlanCourse::of).toList(), Set.of(PaymentMethod.MERCADO_PAGO)
        );
    }

    private static Subscription pendingSubscriptionFor(Payment payment) {
        return Subscription.pendingCheckout(
            UUID.randomUUID(), payment.getId(), USER_ID, PLAN_ID, "idem-1", NOW
        );
    }

    // --- Path 1: the payment already carries the provider id (pre-existing behaviour) ---

    @Test
    void confirms_a_matching_physical_payment_without_billing_assigning_capacity() {
        Payment payment = boundPhysicalPayment(new PaymentStatus.AwaitingProvider());
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(payment));
        when(paymentProviderPort.fetchPayment("mp-1")).thenReturn(approved());

        VerificationOutcome outcome = service.verify("mp-1");

        assertThat(outcome).isInstanceOf(VerificationOutcome.Applied.class);
        Payment updated = ((VerificationOutcome.Applied) outcome).payment();
        assertThat(updated.getStatus()).isEqualTo(new PaymentStatus.Completed(NOW));
        verify(paymentRepository, never()).findByExternalReference(any());
    }

    @Test
    void a_physical_payment_settles_without_invoking_the_removed_billing_fulfillment() {
        Payment payment = boundPhysicalPayment(new PaymentStatus.AwaitingProvider());
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(payment));
        when(paymentProviderPort.fetchPayment("mp-1")).thenReturn(approved());

        VerificationOutcome outcome = service.verify("mp-1");

        Payment updated = ((VerificationOutcome.Applied) outcome).payment();
        assertThat(updated.getStatus()).isInstanceOf(PaymentStatus.Completed.class);
    }

    @Test
    void a_mismatched_provider_response_never_applies_fulfillment() {
        Payment payment = boundPhysicalPayment(new PaymentStatus.AwaitingProvider());
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(payment));
        when(paymentProviderPort.fetchPayment("mp-1"))
            .thenReturn(new ProviderPaymentResult("approved", AMOUNT, "different-ext-ref", "merchant-1"));

        VerificationOutcome outcome = service.verify("mp-1");

        Payment updated = ((VerificationOutcome.Applied) outcome).payment();
        assertThat(updated.getStatus()).isInstanceOf(PaymentStatus.ReconciliationRequired.class);
    }

    @Test
    void an_already_terminal_non_completed_payment_short_circuits_without_calling_the_provider() {
        Payment rejected = boundPhysicalPayment(new PaymentStatus.Rejected(NOW));
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(rejected));

        VerificationOutcome outcome = service.verify("mp-1");

        assertThat(((VerificationOutcome.Applied) outcome).payment().getStatus())
            .isEqualTo(new PaymentStatus.Rejected(NOW));
        verify(paymentProviderPort, never()).fetchPayment(any());
    }

    @Test
    void a_duplicate_webhook_for_an_already_completed_payment_does_not_call_the_provider_again() {
        Payment completed = boundPhysicalPayment(new PaymentStatus.Completed(NOW));
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(completed));
        service.verify("mp-1");

        verify(paymentProviderPort, never()).fetchPayment(any());
    }

    // --- Path 2: the checkout payment is not bound yet ---

    @Test
    void an_unknown_external_reference_never_invents_a_payment() {
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.empty());
        when(paymentProviderPort.fetchPayment("mp-1")).thenReturn(approved());
        when(paymentRepository.findByExternalReference("ext-1")).thenReturn(Optional.empty());

        VerificationOutcome outcome = service.verify("mp-1");

        assertThat(outcome).isInstanceOf(VerificationOutcome.NoLocalPayment.class);
        verify(paymentRepository, never()).save(any());
    }

    /**
     * The correlation key comes from the provider's authenticated response,
     * never from the webhook payload — the webhook only supplies {@code
     * data.id}.
     */
    @Test
    void binds_the_provider_id_to_the_payment_found_by_the_verified_external_reference() {
        Payment payment = unboundVirtualPayment();
        Subscription subscription = pendingSubscriptionFor(payment);
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.empty());
        when(paymentProviderPort.fetchPayment("mp-1")).thenReturn(approved());
        when(paymentRepository.findByExternalReference("ext-1")).thenReturn(Optional.of(payment));
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(subscription));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan("course-1")));

        VerificationOutcome outcome = service.verify("mp-1");

        Payment updated = ((VerificationOutcome.Applied) outcome).payment();
        assertThat(updated.getProviderPaymentId()).contains("mp-1");
        assertThat(updated.getStatus()).isEqualTo(new PaymentStatus.Completed(NOW));
    }

    @Test
    void activates_the_subscription_with_vigencia_and_the_plan_course_snapshot() {
        Payment payment = unboundVirtualPayment();
        Subscription subscription = pendingSubscriptionFor(payment);
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.empty());
        when(paymentProviderPort.fetchPayment("mp-1")).thenReturn(approved());
        when(paymentRepository.findByExternalReference("ext-1")).thenReturn(Optional.of(payment));
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(subscription));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan("course-1", "course-2")));

        service.verify("mp-1");

        var captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        Subscription saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.getStartDate()).contains(NOW);
        assertThat(saved.getEndDate()).contains(Instant.parse("2026-09-17T12:00:00Z"));
        assertThat(saved.getCourseIds()).containsExactly("course-1", "course-2");
        assertThat(saved.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.ASSIGNED);
    }

    @Test
    void virtual_activation_does_not_depend_on_an_external_access_grant() {
        Payment payment = unboundVirtualPayment();
        Subscription subscription = pendingSubscriptionFor(payment);
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.empty());
        when(paymentProviderPort.fetchPayment("mp-1")).thenReturn(approved());
        when(paymentRepository.findByExternalReference("ext-1")).thenReturn(Optional.of(payment));
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(subscription));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan("course-1")));

        VerificationOutcome outcome = service.verify("mp-1");

        var captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getFulfillmentStatus()).isEqualTo(FulfillmentStatus.ASSIGNED);
        assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(((VerificationOutcome.Applied) outcome).payment().getStatus())
            .isInstanceOf(PaymentStatus.Completed.class);
    }

    /** Escenario 2b: the plan may have been deactivated in the meantime; the snapshot still gets taken. */
    @Test
    void activates_against_a_plan_that_is_no_longer_active() {
        Payment payment = unboundVirtualPayment();
        Subscription subscription = pendingSubscriptionFor(payment);
        Plan deactivated = new Plan(
            PLAN_ID, "Plan", "Desc", AMOUNT, 30, false, PlanStatus.INACTIVE, "T", "C",
            List.of(PlanCourse.of("course-1")), Set.of(PaymentMethod.MERCADO_PAGO)
        );
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.empty());
        when(paymentProviderPort.fetchPayment("mp-1")).thenReturn(approved());
        when(paymentRepository.findByExternalReference("ext-1")).thenReturn(Optional.of(payment));
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(subscription));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(deactivated));

        service.verify("mp-1");

        var captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(captor.getValue().getCourseIds()).containsExactly("course-1");
    }

    @Test
    void a_missing_plan_degrades_the_subscription_to_exception_instead_of_an_empty_snapshot() {
        Payment payment = unboundVirtualPayment();
        Subscription subscription = pendingSubscriptionFor(payment);
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.empty());
        when(paymentProviderPort.fetchPayment("mp-1")).thenReturn(approved());
        when(paymentRepository.findByExternalReference("ext-1")).thenReturn(Optional.of(payment));
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(subscription));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

        service.verify("mp-1");

        var captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getFulfillmentStatus()).isEqualTo(FulfillmentStatus.EXCEPTION);
        assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.PENDING);
    }

    @Test
    void a_completed_virtual_payment_with_no_subscription_never_invents_one() {
        Payment completed = boundVirtualPayment(new PaymentStatus.Completed(NOW));
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(completed));
        when(subscriptionRepository.findByPaymentId(completed.getId())).thenReturn(Optional.empty());

        service.verify("mp-1");

        verify(subscriptionRepository, never()).save(any());
    }

    /** Replay: an already-activated subscription must not be re-snapshotted or re-granted. */
    @Test
    void a_replayed_webhook_never_reactivates_an_already_active_subscription() {
        Payment completed = boundVirtualPayment(new PaymentStatus.Completed(NOW));
        Subscription active = pendingSubscriptionFor(completed).activate(NOW, 30, List.of("course-1")).assigned();
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(completed));
        when(subscriptionRepository.findByPaymentId(completed.getId())).thenReturn(Optional.of(active));

        service.verify("mp-1");

        verify(paymentProviderPort, never()).fetchPayment(any());
        verify(subscriptionRepository, never()).save(any());
        verify(planRepository, never()).findById(any());
    }

    /** Escenario 6: a rejected payment releases the user's slot so they can start over. */
    @Test
    void a_rejected_payment_cancels_the_pending_subscription() {
        Payment payment = unboundVirtualPayment();
        Subscription subscription = pendingSubscriptionFor(payment);
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.empty());
        when(paymentProviderPort.fetchPayment("mp-1"))
            .thenReturn(new ProviderPaymentResult("rejected", AMOUNT, "ext-1", "merchant-1"));
        when(paymentRepository.findByExternalReference("ext-1")).thenReturn(Optional.of(payment));
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(subscription));

        VerificationOutcome outcome = service.verify("mp-1");

        assertThat(((VerificationOutcome.Applied) outcome).payment().getStatus())
            .isEqualTo(new PaymentStatus.Rejected(NOW));
        var captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(captor.getValue().occupiesUserSlot()).isFalse();
    }

    @Test
    void a_rejected_payment_with_an_already_released_subscription_writes_nothing() {
        Payment payment = unboundVirtualPayment();
        Subscription cancelled = pendingSubscriptionFor(payment).cancelled();
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.empty());
        when(paymentProviderPort.fetchPayment("mp-1"))
            .thenReturn(new ProviderPaymentResult("cancelled", AMOUNT, "ext-1", "merchant-1"));
        when(paymentRepository.findByExternalReference("ext-1")).thenReturn(Optional.of(payment));
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(cancelled));

        service.verify("mp-1");

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void a_rejected_physical_payment_releases_nothing() {
        Payment payment = Payment.awaitingProvider(
            PaymentId.generate(), USER_ID, AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Physical("session-1"), NOW
        );
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.empty());
        when(paymentProviderPort.fetchPayment("mp-1"))
            .thenReturn(new ProviderPaymentResult("expired", AMOUNT, "ext-1", "merchant-1"));
        when(paymentRepository.findByExternalReference("ext-1")).thenReturn(Optional.of(payment));

        service.verify("mp-1");

        verify(subscriptionRepository, never()).findByPaymentId(any());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void a_still_pending_provider_status_leaves_the_payment_unfulfilled_and_the_subscription_untouched() {
        Payment payment = unboundVirtualPayment();
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.empty());
        when(paymentProviderPort.fetchPayment("mp-1"))
            .thenReturn(new ProviderPaymentResult("in_process", AMOUNT, "ext-1", "merchant-1"));
        when(paymentRepository.findByExternalReference("ext-1")).thenReturn(Optional.of(payment));

        VerificationOutcome outcome = service.verify("mp-1");

        assertThat(((VerificationOutcome.Applied) outcome).payment().getStatus())
            .isEqualTo(new PaymentStatus.AwaitingProvider());
        verify(subscriptionRepository, never()).save(any());
    }

    /** A mismatch on the unbound path must never bind the provider id it failed to verify. */
    @Test
    void a_mismatch_on_the_external_reference_path_never_binds_the_provider_id() {
        Payment payment = Payment.awaitingProvider(
            PaymentId.generate(), USER_ID, Money.of(BigDecimal.ONE, "ARS"), "ext-1", "merchant-1",
            new PaymentTarget.Virtual(PLAN_ID.toString()), NOW
        );
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.empty());
        when(paymentProviderPort.fetchPayment("mp-1")).thenReturn(approved());
        when(paymentRepository.findByExternalReference("ext-1")).thenReturn(Optional.of(payment));

        VerificationOutcome outcome = service.verify("mp-1");

        Payment updated = ((VerificationOutcome.Applied) outcome).payment();
        assertThat(updated.getStatus()).isInstanceOf(PaymentStatus.ReconciliationRequired.class);
        assertThat(updated.getProviderPaymentId()).isEmpty();
        verify(subscriptionRepository, never()).save(any());
    }

    /**
     * Two provider transactions claiming one local payment: the existing
     * binding stands and the worker raises a reconciliation task from the
     * resulting status.
     */
    @Test
    void a_second_provider_payment_on_the_same_reference_goes_to_reconciliation_without_rebinding() {
        Payment payment = boundVirtualPayment(new PaymentStatus.AwaitingProvider());
        when(paymentRepository.findByProviderPaymentId("mp-2")).thenReturn(Optional.empty());
        when(paymentProviderPort.fetchPayment("mp-2")).thenReturn(approved());
        when(paymentRepository.findByExternalReference("ext-1")).thenReturn(Optional.of(payment));

        VerificationOutcome outcome = service.verify("mp-2");

        Payment updated = ((VerificationOutcome.Applied) outcome).payment();
        assertThat(updated.getStatus()).isInstanceOf(PaymentStatus.ReconciliationRequired.class);
        assertThat(updated.getProviderPaymentId()).contains("mp-1");
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void an_unbound_payment_already_terminal_short_circuits_on_the_external_reference_path() {
        Payment expired = new Payment(
            PaymentId.generate(), USER_ID, null, AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Virtual(PLAN_ID.toString()), new PaymentStatus.Expired(NOW), NOW
        );
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.empty());
        when(paymentProviderPort.fetchPayment("mp-1")).thenReturn(approved());
        when(paymentRepository.findByExternalReference("ext-1")).thenReturn(Optional.of(expired));

        VerificationOutcome outcome = service.verify("mp-1");

        assertThat(((VerificationOutcome.Applied) outcome).payment().getStatus())
            .isEqualTo(new PaymentStatus.Expired(NOW));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void an_unbound_completed_payment_re_ensures_fulfillment_idempotently() {
        Payment completed = new Payment(
            PaymentId.generate(), USER_ID, null, AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Virtual(PLAN_ID.toString()), new PaymentStatus.Completed(NOW), NOW
        );
        Subscription active = pendingSubscriptionFor(completed).activate(NOW, 30, List.of("course-1")).assigned();
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.empty());
        when(paymentProviderPort.fetchPayment("mp-1")).thenReturn(approved());
        when(paymentRepository.findByExternalReference("ext-1")).thenReturn(Optional.of(completed));
        when(subscriptionRepository.findByPaymentId(completed.getId())).thenReturn(Optional.of(active));

        service.verify("mp-1");

        verify(subscriptionRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void a_replayed_completed_payment_recovers_an_exceptional_subscription_snapshot() {
        Payment completed = boundVirtualPayment(new PaymentStatus.Completed(NOW));
        Subscription exceptional = pendingSubscriptionFor(completed)
            .activate(NOW, 30, List.of("course-1"))
            .exception();
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(completed));
        when(subscriptionRepository.findByPaymentId(completed.getId())).thenReturn(Optional.of(exceptional));

        service.verify("mp-1");

        var captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getFulfillmentStatus()).isEqualTo(FulfillmentStatus.ASSIGNED);
        verify(planRepository, never()).findById(any());
    }

    @Test
    void retries_a_subscription_left_exceptional_by_the_first_fulfillment_attempt() {
        Payment awaiting = boundVirtualPayment(new PaymentStatus.AwaitingProvider());
        Payment completed = awaiting.applyProviderOutcome(
            new com.menta.billing.domain.model.ProviderOutcome(
                "approved", AMOUNT, "ext-1", "merchant-1"
            ),
            NOW
        );
        Subscription pending = pendingSubscriptionFor(awaiting);
        when(paymentRepository.findByProviderPaymentId("mp-1"))
            .thenReturn(Optional.of(awaiting), Optional.of(completed));
        when(paymentProviderPort.fetchPayment("mp-1")).thenReturn(approved());
        when(subscriptionRepository.findByPaymentId(awaiting.getId())).thenReturn(Optional.of(pending));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty(), Optional.of(plan("course-1")));

        service.verify("mp-1");
        service.verify("mp-1");

        var captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(Subscription::getFulfillmentStatus)
            .containsExactly(FulfillmentStatus.EXCEPTION, FulfillmentStatus.ASSIGNED);
    }

    /** The confirmation instant comes from the payment; the clock is only a fallback. */
    @Test
    void activation_falls_back_to_the_clock_when_the_payment_carries_no_confirmation_instant() {
        Payment completed = boundVirtualPayment(new PaymentStatus.Completed(Instant.parse("2026-07-01T00:00:00Z")));
        Subscription subscription = pendingSubscriptionFor(completed);
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(completed));
        when(subscriptionRepository.findByPaymentId(completed.getId())).thenReturn(Optional.of(subscription));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan("course-1")));

        service.verify("mp-1");

        var captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getStartDate()).contains(Instant.parse("2026-07-01T00:00:00Z"));
    }
}
