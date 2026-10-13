package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.billing.application.contract.BillingOutboxEventTypes;
import com.menta.billing.application.dto.PurchaseResolutionResult;
import com.menta.billing.application.dto.ResolveExceptionPurchaseCommand;
import com.menta.billing.application.port.out.BillingOutboxAppenderPort;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.domain.exception.IllegalPurchaseStateTransitionException;
import com.menta.billing.domain.exception.PurchaseNotFoundException;
import com.menta.billing.domain.exception.PurchaseResolutionConflictException;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.domain.model.PurchaseResolutionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("ResolveExceptionPurchaseUseCaseImpl: admin closes an EXCEPTION purchase with a record")
class ResolveExceptionPurchaseUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");
    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final PaymentId PAYMENT_ID = PaymentId.of(UUID.fromString("55555555-5555-5555-5555-555555555555"));

    private PurchaseRepository purchaseRepository;
    private PaymentRepository paymentRepository;
    private BillingOutboxAppenderPort outboxAppender;
    private ResolveExceptionPurchaseUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        purchaseRepository = mock(PurchaseRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        outboxAppender = mock(BillingOutboxAppenderPort.class);
        Clock clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(new Payment(
            PAYMENT_ID, USER_ID, "mp-1", Money.of(new BigDecimal("1500.00"), "ARS"), "ext-1", "merchant-1",
            new PaymentTarget.Physical("quote-1"), new PaymentStatus.Completed(NOW), NOW
        )));
        useCase = new ResolveExceptionPurchaseUseCaseImpl(
            purchaseRepository, paymentRepository, outboxAppender, clock);
    }

    private Purchase givenPurchase(Purchase purchase) {
        when(purchaseRepository.findByIdForUpdate(purchase.getId())).thenReturn(Optional.of(purchase));
        return purchase;
    }

    private static ResolveExceptionPurchaseCommand command(
        Purchase purchase, PurchaseResolutionType type, String note
    ) {
        return new ResolveExceptionPurchaseCommand(purchase.getId(), type, note, ADMIN_ID);
    }

    @Test
    void resolves_persists_and_appends_exactly_one_purchase_resolved_event() {
        Purchase purchase = givenPurchase(Purchase.exception(PAYMENT_ID, List.of("session-1")));

        PurchaseResolutionResult result =
            useCase.resolve(command(purchase, PurchaseResolutionType.REFUNDED, "Reembolsado"));

        assertThat(result.status()).isEqualTo("RESOLVED");
        assertThat(result.resolution()).isEqualTo("REFUNDED");
        assertThat(result.note()).isEqualTo("Reembolsado");
        assertThat(result.resolvedBy()).isEqualTo(ADMIN_ID);
        assertThat(result.resolvedAt()).isEqualTo(NOW);
        assertThat(result.purchaseId()).isEqualTo(purchase.getId());
        assertThat(result.paymentId()).isEqualTo(PAYMENT_ID.getValue());
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outboxAppender).append(
            eq(BillingOutboxEventTypes.PURCHASE_RESOLVED), eq(PAYMENT_ID.getValue().toString()), payload.capture());
        assertThat(payload.getValue())
            .contains(purchase.getId().toString(), USER_ID.toString(), ADMIN_ID.toString(), "REFUNDED")
            .doesNotContain("Reembolsado");
    }

    @Test
    void resolving_twice_with_the_same_decision_is_a_no_op_without_a_second_event() {
        Purchase resolved = givenPurchase(Purchase.exception(PAYMENT_ID, List.of("session-1"))
            .resolve(PurchaseResolutionType.COMPENSATED, "Compensada", ADMIN_ID, NOW.minusSeconds(60)));

        PurchaseResolutionResult result =
            useCase.resolve(command(resolved, PurchaseResolutionType.COMPENSATED, "Compensada"));

        assertThat(result.status()).isEqualTo("RESOLVED");
        assertThat(result.resolvedAt()).isEqualTo(NOW.minusSeconds(60));
        verify(purchaseRepository, never()).save(any());
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void a_conflicting_resolution_is_refused_without_writes() {
        Purchase resolved = givenPurchase(Purchase.exception(PAYMENT_ID, List.of("session-1"))
            .resolve(PurchaseResolutionType.REFUNDED, "Reembolsado", ADMIN_ID, NOW));

        assertThatThrownBy(() -> useCase.resolve(command(resolved, PurchaseResolutionType.COMPENSATED, "Otra")))
            .isInstanceOf(PurchaseResolutionConflictException.class);
        verify(purchaseRepository, never()).save(any());
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void a_purchase_that_is_not_in_exception_is_refused_without_writes() {
        Purchase assigned = givenPurchase(Purchase.pendingFulfillment(PAYMENT_ID, List.of("session-1")).assigned());

        assertThatThrownBy(() -> useCase.resolve(command(assigned, PurchaseResolutionType.REFUNDED, "Nota")))
            .isInstanceOf(IllegalPurchaseStateTransitionException.class);
        verify(purchaseRepository, never()).save(any());
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void an_unknown_purchase_is_not_found() {
        UUID unknown = UUID.randomUUID();
        when(purchaseRepository.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.resolve(
            new ResolveExceptionPurchaseCommand(unknown, PurchaseResolutionType.REFUNDED, "Nota", ADMIN_ID)))
            .isInstanceOf(PurchaseNotFoundException.class);
        verifyNoInteractions(outboxAppender);
    }
}
