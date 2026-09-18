package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.port.out.Clock;
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

/**
 * Unit tests for the collaborator extracted from {@code PaymentVerificationService} (design C10)
 * — {@code ensure}/{@code release} must behave exactly as the removed private {@code
 * ensureFulfillment}/{@code releaseFulfillment}/{@code ensureSubscription} methods did. {@code
 * PaymentVerificationServiceTest} stays green and unmodified as the refactor's own proof.
 */
class PaymentFulfillmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final Money AMOUNT = Money.of(BigDecimal.TEN, "ARS");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final PlanId PLAN_ID = PlanId.generate();

    private SubscriptionRepository subscriptionRepository;
    private PlanRepository planRepository;
    private Clock clock;
    private PublishPhysicalPaymentCompletedUseCase publishPhysicalPaymentCompletedUseCase;
    private PaymentFulfillmentService service;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        planRepository = mock(PlanRepository.class);
        clock = mock(Clock.class);
        publishPhysicalPaymentCompletedUseCase = mock(PublishPhysicalPaymentCompletedUseCase.class);
        when(clock.now()).thenReturn(NOW);
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new PaymentFulfillmentService(
            subscriptionRepository, planRepository, clock, publishPhysicalPaymentCompletedUseCase
        );
    }

    private static Payment virtualPayment(PaymentStatus status) {
        return new Payment(
            PaymentId.generate(), USER_ID, "mp-1", AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Virtual(PLAN_ID.toString()), status, NOW
        );
    }

    private static Payment physicalPayment(PaymentStatus status) {
        return new Payment(
            PaymentId.generate(), USER_ID, "mp-1", AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Physical("session-1"), status, NOW
        );
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

    // --- ensure(payment): PaymentTarget.Virtual ---

    @Test
    void ensure_activates_the_subscription_with_vigencia_and_the_plan_course_snapshot() {
        Payment payment = virtualPayment(new PaymentStatus.Completed(NOW));
        Subscription subscription = pendingSubscriptionFor(payment);
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(subscription));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan("course-1", "course-2")));

        service.ensure(payment);

        var captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        Subscription saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.getStartDate()).contains(NOW);
        assertThat(saved.getCourseIds()).containsExactly("course-1", "course-2");
        assertThat(saved.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.ASSIGNED);
    }

    /** Escenario 2b: reads the plan by id regardless of its own status. */
    @Test
    void ensure_reads_the_plan_by_id_regardless_of_the_plans_own_status() {
        Payment payment = virtualPayment(new PaymentStatus.Completed(NOW));
        Subscription subscription = pendingSubscriptionFor(payment);
        Plan deactivated = new Plan(
            PLAN_ID, "Plan", "Desc", AMOUNT, 30, false, PlanStatus.INACTIVE, "T", "C",
            List.of(PlanCourse.of("course-1")), Set.of(PaymentMethod.MERCADO_PAGO)
        );
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(subscription));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(deactivated));

        service.ensure(payment);

        var captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(captor.getValue().getCourseIds()).containsExactly("course-1");
    }

    @Test
    void ensure_degrades_to_exception_when_the_plan_no_longer_exists() {
        Payment payment = virtualPayment(new PaymentStatus.Completed(NOW));
        Subscription subscription = pendingSubscriptionFor(payment);
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(subscription));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

        service.ensure(payment);

        var captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getFulfillmentStatus()).isEqualTo(FulfillmentStatus.EXCEPTION);
        assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.PENDING);
    }

    @Test
    void ensure_is_idempotent_once_the_subscription_is_already_active_and_assigned() {
        Payment payment = virtualPayment(new PaymentStatus.Completed(NOW));
        Subscription active = pendingSubscriptionFor(payment).activate(NOW, 30, List.of("course-1")).assigned();
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(active));

        service.ensure(payment);

        verify(subscriptionRepository, never()).save(any());
        verify(planRepository, never()).findById(any());
    }

    @Test
    void ensure_re_grants_an_active_but_unassigned_subscription_without_touching_the_plan() {
        Payment payment = virtualPayment(new PaymentStatus.Completed(NOW));
        Subscription activeUnassigned = pendingSubscriptionFor(payment).activate(NOW, 30, List.of("course-1"));
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(activeUnassigned));

        service.ensure(payment);

        var captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getFulfillmentStatus()).isEqualTo(FulfillmentStatus.ASSIGNED);
        verify(planRepository, never()).findById(any());
    }

    @Test
    void ensure_never_invents_a_subscription_when_none_exists() {
        Payment payment = virtualPayment(new PaymentStatus.Completed(NOW));
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.empty());

        service.ensure(payment);

        verify(subscriptionRepository, never()).save(any());
    }

    /** The confirmation instant comes from the payment; the clock is only a fallback. */
    @Test
    void ensure_uses_the_payments_own_confirmation_instant_when_present() {
        Instant confirmedAt = Instant.parse("2026-07-01T00:00:00Z");
        Payment payment = virtualPayment(new PaymentStatus.Completed(confirmedAt));
        Subscription subscription = pendingSubscriptionFor(payment);
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(subscription));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan("course-1")));

        service.ensure(payment);

        var captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getStartDate()).contains(confirmedAt);
    }

    @Test
    void ensure_falls_back_to_the_clock_when_the_payment_has_no_confirmation_instant() {
        Payment payment = virtualPayment(new PaymentStatus.AwaitingManualVerification());
        Subscription subscription = pendingSubscriptionFor(payment);
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(subscription));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan("course-1")));

        service.ensure(payment);

        var captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getStartDate()).contains(NOW);
    }

    // --- ensure(payment): PaymentTarget.Physical ---

    @Test
    void ensure_delegates_a_physical_payment_to_the_physical_completion_use_case() {
        Payment payment = physicalPayment(new PaymentStatus.Completed(NOW));

        service.ensure(payment);

        verify(publishPhysicalPaymentCompletedUseCase).handle(payment);
        verify(subscriptionRepository, never()).findByPaymentId(any());
    }

    // --- release(payment): PaymentTarget.Virtual ---

    @Test
    void release_cancels_a_pending_subscription_still_occupying_a_slot() {
        Payment payment = virtualPayment(new PaymentStatus.Rejected(NOW));
        Subscription subscription = pendingSubscriptionFor(payment);
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(subscription));

        service.release(payment);

        var captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(captor.getValue().occupiesUserSlot()).isFalse();
    }

    @Test
    void release_writes_nothing_when_the_slot_is_already_released() {
        Payment payment = virtualPayment(new PaymentStatus.Cancelled(NOW));
        Subscription alreadyCancelled = pendingSubscriptionFor(payment).cancelled();
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(alreadyCancelled));

        service.release(payment);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void release_writes_nothing_when_no_subscription_exists_for_the_payment() {
        Payment payment = virtualPayment(new PaymentStatus.Expired(NOW));
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.empty());

        service.release(payment);

        verify(subscriptionRepository, never()).save(any());
    }

    // --- release(payment): PaymentTarget.Physical ---

    @Test
    void release_never_looks_up_a_subscription_for_a_physical_payment() {
        Payment payment = physicalPayment(new PaymentStatus.Expired(NOW));

        service.release(payment);

        verify(subscriptionRepository, never()).findByPaymentId(any());
        verify(subscriptionRepository, never()).save(any());
    }
}
