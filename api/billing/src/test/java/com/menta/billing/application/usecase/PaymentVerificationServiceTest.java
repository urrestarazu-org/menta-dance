package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.ProviderPaymentResult;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PhysicalCapacityAssignmentPort;
import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.application.port.out.VirtualAccessGrantPort;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.domain.model.Subscription;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final Money AMOUNT = Money.of(BigDecimal.TEN, "ARS");

    private PaymentRepository paymentRepository;
    private PaymentProviderPort paymentProviderPort;
    private PurchaseRepository purchaseRepository;
    private SubscriptionRepository subscriptionRepository;
    private PhysicalCapacityAssignmentPort physicalCapacityAssignmentPort;
    private VirtualAccessGrantPort virtualAccessGrantPort;
    private Clock clock;
    private PaymentVerificationService service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        paymentProviderPort = mock(PaymentProviderPort.class);
        purchaseRepository = mock(PurchaseRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        physicalCapacityAssignmentPort = mock(PhysicalCapacityAssignmentPort.class);
        virtualAccessGrantPort = mock(VirtualAccessGrantPort.class);
        clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new PaymentVerificationService(
            paymentRepository, paymentProviderPort, purchaseRepository, subscriptionRepository,
            physicalCapacityAssignmentPort, virtualAccessGrantPort, clock
        );
    }

    private static Payment pendingPhysicalPayment() {
        return new Payment(
            PaymentId.generate(), "mp-1", AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Physical("session-1"), new PaymentStatus.AwaitingProvider(), NOW
        );
    }

    private static Payment pendingVirtualPayment() {
        return new Payment(
            PaymentId.generate(), "mp-1", AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Virtual("course-1"), new PaymentStatus.AwaitingProvider(), NOW
        );
    }

    @Test
    void returns_no_local_payment_when_none_matches_the_provider_id() {
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.empty());

        VerificationOutcome outcome = service.verify("mp-1");

        assertThat(outcome).isInstanceOf(VerificationOutcome.NoLocalPayment.class);
        verify(paymentProviderPort, never()).fetchPayment(any());
    }

    @Test
    void confirms_a_matching_physical_payment_and_assigns_capacity() {
        Payment payment = pendingPhysicalPayment();
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(payment));
        when(paymentProviderPort.fetchPayment("mp-1"))
            .thenReturn(new ProviderPaymentResult("approved", AMOUNT, "ext-1", "merchant-1"));
        when(purchaseRepository.findByPaymentId(payment.getId())).thenReturn(Optional.empty());

        VerificationOutcome outcome = service.verify("mp-1");

        assertThat(outcome).isInstanceOf(VerificationOutcome.Applied.class);
        Payment updated = ((VerificationOutcome.Applied) outcome).payment();
        assertThat(updated.getStatus()).isEqualTo(new PaymentStatus.Completed(NOW));
        verify(physicalCapacityAssignmentPort).assign(eq("session-1"), any());
        var captor = org.mockito.ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(FulfillmentStatus.ASSIGNED);
    }

    @Test
    void a_failed_capacity_assignment_degrades_the_purchase_to_exception_but_keeps_the_payment_completed() {
        Payment payment = pendingPhysicalPayment();
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(payment));
        when(paymentProviderPort.fetchPayment("mp-1"))
            .thenReturn(new ProviderPaymentResult("approved", AMOUNT, "ext-1", "merchant-1"));
        when(purchaseRepository.findByPaymentId(payment.getId())).thenReturn(Optional.empty());
        doThrow(new UnsupportedOperationException("not implemented"))
            .when(physicalCapacityAssignmentPort).assign(any(), any());

        VerificationOutcome outcome = service.verify("mp-1");

        Payment updated = ((VerificationOutcome.Applied) outcome).payment();
        assertThat(updated.getStatus()).isInstanceOf(PaymentStatus.Completed.class);
        var captor = org.mockito.ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(FulfillmentStatus.EXCEPTION);
    }

    @Test
    void confirms_a_matching_virtual_payment_and_grants_access() {
        Payment payment = pendingVirtualPayment();
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(payment));
        when(paymentProviderPort.fetchPayment("mp-1"))
            .thenReturn(new ProviderPaymentResult("approved", AMOUNT, "ext-1", "merchant-1"));
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.empty());

        service.verify("mp-1");

        verify(virtualAccessGrantPort).grant(eq("course-1"), any());
        var captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(FulfillmentStatus.ASSIGNED);
    }

    @Test
    void a_failed_access_grant_degrades_the_subscription_to_exception() {
        Payment payment = pendingVirtualPayment();
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(payment));
        when(paymentProviderPort.fetchPayment("mp-1"))
            .thenReturn(new ProviderPaymentResult("approved", AMOUNT, "ext-1", "merchant-1"));
        when(subscriptionRepository.findByPaymentId(payment.getId())).thenReturn(Optional.empty());
        doThrow(new UnsupportedOperationException("not implemented"))
            .when(virtualAccessGrantPort).grant(any(), any());

        service.verify("mp-1");

        var captor = org.mockito.ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(FulfillmentStatus.EXCEPTION);
    }

    @Test
    void a_mismatched_provider_response_never_applies_fulfillment() {
        Payment payment = pendingPhysicalPayment();
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(payment));
        when(paymentProviderPort.fetchPayment("mp-1"))
            .thenReturn(new ProviderPaymentResult("approved", AMOUNT, "different-ext-ref", "merchant-1"));

        VerificationOutcome outcome = service.verify("mp-1");

        Payment updated = ((VerificationOutcome.Applied) outcome).payment();
        assertThat(updated.getStatus()).isInstanceOf(PaymentStatus.ReconciliationRequired.class);
        verify(physicalCapacityAssignmentPort, never()).assign(any(), any());
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void an_already_terminal_non_completed_payment_short_circuits_without_calling_the_provider() {
        Payment rejected = new Payment(
            PaymentId.generate(), "mp-1", AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Physical("session-1"), new PaymentStatus.Rejected(NOW), NOW
        );
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(rejected));

        VerificationOutcome outcome = service.verify("mp-1");

        assertThat(((VerificationOutcome.Applied) outcome).payment().getStatus())
            .isEqualTo(new PaymentStatus.Rejected(NOW));
        verify(paymentProviderPort, never()).fetchPayment(any());
    }

    @Test
    void a_duplicate_webhook_for_an_already_completed_payment_does_not_call_the_provider_again() {
        Payment completed = new Payment(
            PaymentId.generate(), "mp-1", AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Physical("session-1"), new PaymentStatus.Completed(NOW), NOW
        );
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(completed));
        when(purchaseRepository.findByPaymentId(completed.getId())).thenReturn(Optional.empty());

        service.verify("mp-1");

        verify(paymentProviderPort, never()).fetchPayment(any());
    }

    @Test
    void a_duplicate_webhook_never_re_attempts_fulfillment_once_a_purchase_already_exists() {
        Payment completed = new Payment(
            PaymentId.generate(), "mp-1", AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Physical("session-1"), new PaymentStatus.Completed(NOW), NOW
        );
        Purchase existing = Purchase.pendingFulfillment(completed.getId(), "session-1").assigned();
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(completed));
        when(purchaseRepository.findByPaymentId(completed.getId())).thenReturn(Optional.of(existing));

        service.verify("mp-1");

        verify(physicalCapacityAssignmentPort, never()).assign(any(), any());
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void a_duplicate_webhook_never_re_attempts_fulfillment_once_a_subscription_already_exists() {
        Payment completed = new Payment(
            PaymentId.generate(), "mp-1", AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Virtual("course-1"), new PaymentStatus.Completed(NOW), NOW
        );
        Subscription existing = Subscription.pendingFulfillment(completed.getId(), "course-1").assigned();
        when(paymentRepository.findByProviderPaymentId("mp-1")).thenReturn(Optional.of(completed));
        when(subscriptionRepository.findByPaymentId(completed.getId())).thenReturn(Optional.of(existing));

        service.verify("mp-1");

        verify(virtualAccessGrantPort, never()).grant(any(), any());
        verify(subscriptionRepository, never()).save(any());
    }
}
