package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.billing.domain.exception.IllegalPurchaseStateTransitionException;
import com.menta.billing.domain.exception.PurchaseResolutionConflictException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Purchase.resolve: EXCEPTION -> RESOLVED with a record of the decision")
class PurchaseResolveTest {

    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");

    private static Purchase exceptionPurchase() {
        return Purchase.exception(PaymentId.generate(), List.of("session-1"));
    }

    @Test
    void resolves_an_exception_purchase_recording_who_when_and_why() {
        Purchase resolved = exceptionPurchase()
            .resolve(PurchaseResolutionType.REFUNDED, "  Reembolsado por transferencia  ", ADMIN_ID, NOW);

        assertThat(resolved.getStatus()).isEqualTo(FulfillmentStatus.RESOLVED);
        PurchaseResolution resolution = resolved.getResolution().orElseThrow();
        assertThat(resolution.type()).isEqualTo(PurchaseResolutionType.REFUNDED);
        assertThat(resolution.note()).isEqualTo("Reembolsado por transferencia");
        assertThat(resolution.resolvedBy()).isEqualTo(ADMIN_ID);
        assertThat(resolution.resolvedAt()).isEqualTo(NOW);
        assertThat(resolved.grantsAttendance()).isFalse();
    }

    @Test
    void keeps_id_payment_and_sessions() {
        Purchase original = exceptionPurchase();

        Purchase resolved = original.resolve(PurchaseResolutionType.COMPENSATED, "Compensada", ADMIN_ID, NOW);

        assertThat(resolved.getId()).isEqualTo(original.getId());
        assertThat(resolved.getPaymentId()).isEqualTo(original.getPaymentId());
        assertThat(resolved.getPhysicalSessionIds()).containsExactly("session-1");
    }

    @Test
    void resolves_a_zero_session_exception_purchase() {
        Purchase resolved = Purchase.exception(PaymentId.generate(), List.of())
            .resolve(PurchaseResolutionType.REFUNDED, "Reembolsado", ADMIN_ID, NOW);

        assertThat(resolved.getStatus()).isEqualTo(FulfillmentStatus.RESOLVED);
        assertThat(resolved.getPhysicalSessionIds()).isEmpty();
    }

    @Test
    void resolving_again_with_the_same_resolution_and_note_is_a_no_op() {
        Purchase resolved = exceptionPurchase()
            .resolve(PurchaseResolutionType.REFUNDED, "Reembolsado", ADMIN_ID, NOW);

        Purchase again = resolved.resolve(
            PurchaseResolutionType.REFUNDED, " Reembolsado ", UUID.randomUUID(), NOW.plusSeconds(60));

        assertThat(again).isSameAs(resolved);
        assertThat(resolved.isResolvedAs(PurchaseResolutionType.REFUNDED, "Reembolsado")).isTrue();
    }

    @Test
    void resolving_again_with_a_different_resolution_is_a_conflict() {
        Purchase resolved = exceptionPurchase()
            .resolve(PurchaseResolutionType.REFUNDED, "Reembolsado", ADMIN_ID, NOW);

        assertThatThrownBy(() -> resolved.resolve(PurchaseResolutionType.COMPENSATED, "Reembolsado", ADMIN_ID, NOW))
            .isInstanceOf(PurchaseResolutionConflictException.class);
    }

    @Test
    void resolving_again_with_a_different_note_is_a_conflict() {
        Purchase resolved = exceptionPurchase()
            .resolve(PurchaseResolutionType.REFUNDED, "Reembolsado", ADMIN_ID, NOW);

        assertThatThrownBy(() -> resolved.resolve(PurchaseResolutionType.REFUNDED, "Otra nota", ADMIN_ID, NOW))
            .isInstanceOf(PurchaseResolutionConflictException.class);
    }

    @Test
    void only_exception_purchases_can_be_resolved() {
        Purchase pending = Purchase.pendingFulfillment(PaymentId.generate(), List.of("session-1"));

        assertThatThrownBy(() -> pending.resolve(PurchaseResolutionType.REFUNDED, "Nota", ADMIN_ID, NOW))
            .isInstanceOf(IllegalPurchaseStateTransitionException.class);
        assertThatThrownBy(() -> pending.assigned().resolve(PurchaseResolutionType.REFUNDED, "Nota", ADMIN_ID, NOW))
            .isInstanceOf(IllegalPurchaseStateTransitionException.class);
    }

    @Test
    void rejects_a_blank_note() {
        assertThatThrownBy(() -> exceptionPurchase().resolve(PurchaseResolutionType.REFUNDED, "   ", ADMIN_ID, NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_note_longer_than_the_limit() {
        String tooLong = "x".repeat(PurchaseResolution.MAX_NOTE_LENGTH + 1);

        assertThatThrownBy(() -> exceptionPurchase().resolve(PurchaseResolutionType.REFUNDED, tooLong, ADMIN_ID, NOW))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(exceptionPurchase()
            .resolve(PurchaseResolutionType.REFUNDED, "x".repeat(PurchaseResolution.MAX_NOTE_LENGTH), ADMIN_ID, NOW)
            .getStatus()).isEqualTo(FulfillmentStatus.RESOLVED);
    }

    @Test
    void a_resolved_purchase_without_a_resolution_record_is_invalid() {
        PaymentId paymentId = PaymentId.generate();
        List<String> sessions = List.of("session-1");

        assertThatThrownBy(() -> new Purchase(UUID.randomUUID(), paymentId, sessions, FulfillmentStatus.RESOLVED))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_resolution_record_on_a_non_resolved_purchase_is_invalid() {
        PaymentId paymentId = PaymentId.generate();
        List<String> sessions = List.of("session-1");
        PurchaseResolution resolution =
            new PurchaseResolution(PurchaseResolutionType.REFUNDED, "Nota", ADMIN_ID, NOW);

        assertThatThrownBy(
            () -> new Purchase(UUID.randomUUID(), paymentId, sessions, FulfillmentStatus.EXCEPTION, resolution))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void is_not_resolved_as_anything_while_in_exception() {
        assertThat(exceptionPurchase().isResolvedAs(PurchaseResolutionType.REFUNDED, "Nota")).isFalse();
        assertThat(exceptionPurchase().getResolution()).isEmpty();
    }
}
