package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-18T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final Money EXPECTED_AMOUNT = Money.of(BigDecimal.TEN, "ARS");

    private static Payment pendingPayment() {
        return new Payment(
            PaymentId.generate(), "mp-1", EXPECTED_AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Physical("session-1"), new PaymentStatus.AwaitingProvider(), CREATED_AT
        );
    }

    private static Payment terminalPayment(PaymentStatus status) {
        return new Payment(
            PaymentId.generate(), "mp-1", EXPECTED_AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Physical("session-1"), status, CREATED_AT
        );
    }

    private static ProviderOutcome matchingOutcome(String providerStatus) {
        return new ProviderOutcome(providerStatus, EXPECTED_AMOUNT, "ext-1", "merchant-1");
    }

    @Test
    void matchesExpected_true_when_every_field_matches() {
        assertThat(pendingPayment().matchesExpected(matchingOutcome("approved"))).isTrue();
    }

    @Test
    void matchesExpected_false_on_amount_mismatch() {
        ProviderOutcome mismatched =
            new ProviderOutcome("approved", Money.of(BigDecimal.ONE, "ARS"), "ext-1", "merchant-1");
        assertThat(pendingPayment().matchesExpected(mismatched)).isFalse();
    }

    @Test
    void matchesExpected_false_on_external_reference_mismatch() {
        ProviderOutcome mismatched = new ProviderOutcome("approved", EXPECTED_AMOUNT, "ext-other", "merchant-1");
        assertThat(pendingPayment().matchesExpected(mismatched)).isFalse();
    }

    @Test
    void matchesExpected_false_on_merchant_account_mismatch() {
        ProviderOutcome mismatched = new ProviderOutcome("approved", EXPECTED_AMOUNT, "ext-1", "merchant-other");
        assertThat(pendingPayment().matchesExpected(mismatched)).isFalse();
    }

    @Test
    void applyProviderOutcome_transitions_a_matching_pending_payment() {
        Payment updated = pendingPayment().applyProviderOutcome(matchingOutcome("approved"), NOW);

        assertThat(updated.getStatus()).isEqualTo(new PaymentStatus.Completed(NOW));
        assertThat(updated.confirmedAt()).contains(NOW);
    }

    @Test
    void applyProviderOutcome_routes_a_mismatch_to_reconciliation_required_regardless_of_provider_status() {
        ProviderOutcome mismatched = new ProviderOutcome("approved", EXPECTED_AMOUNT, "ext-other", "merchant-1");

        Payment updated = pendingPayment().applyProviderOutcome(mismatched, NOW);

        assertThat(updated.getStatus())
            .isEqualTo(new PaymentStatus.ReconciliationRequired("provider outcome does not match expected payment fields"));
    }

    @Test
    void applyProviderOutcome_is_a_no_op_on_every_terminal_status() {
        for (PaymentStatus terminal : new PaymentStatus[] {
            new PaymentStatus.Completed(CREATED_AT), new PaymentStatus.Rejected(CREATED_AT),
            new PaymentStatus.Cancelled(CREATED_AT), new PaymentStatus.Expired(CREATED_AT)
        }) {
            Payment payment = terminalPayment(terminal);

            Payment result = payment.applyProviderOutcome(matchingOutcome("approved"), NOW);

            assertThat(result.getStatus()).isEqualTo(terminal);
        }
    }

    @Test
    void markReconciliationRequired_applies_to_a_pending_payment() {
        Payment updated = pendingPayment().markReconciliationRequired("provider lookup failed");

        assertThat(updated.getStatus()).isEqualTo(new PaymentStatus.ReconciliationRequired("provider lookup failed"));
    }

    @Test
    void markReconciliationRequired_is_a_no_op_on_every_terminal_status() {
        for (PaymentStatus terminal : new PaymentStatus[] {
            new PaymentStatus.Completed(CREATED_AT), new PaymentStatus.Rejected(CREATED_AT),
            new PaymentStatus.Cancelled(CREATED_AT), new PaymentStatus.Expired(CREATED_AT)
        }) {
            Payment payment = terminalPayment(terminal);

            assertThat(payment.markReconciliationRequired("x").getStatus()).isEqualTo(terminal);
        }
    }

    @Test
    void isTerminal_reflects_status() {
        assertThat(pendingPayment().isTerminal()).isFalse();
        assertThat(terminalPayment(new PaymentStatus.Completed(CREATED_AT)).isTerminal()).isTrue();
    }

    @Test
    void confirmedAt_is_empty_unless_completed() {
        assertThat(pendingPayment().confirmedAt()).isEqualTo(Optional.empty());
        assertThat(terminalPayment(new PaymentStatus.Rejected(CREATED_AT)).confirmedAt()).isEqualTo(Optional.empty());
    }

    @Test
    void exposes_every_field_passed_at_construction() {
        Payment payment = pendingPayment();

        assertThat(payment.getProviderPaymentId()).isEqualTo("mp-1");
        assertThat(payment.getExpectedAmount()).isEqualTo(EXPECTED_AMOUNT);
        assertThat(payment.getExpectedExternalReference()).isEqualTo("ext-1");
        assertThat(payment.getExpectedMerchantAccountId()).isEqualTo("merchant-1");
        assertThat(payment.getTarget()).isEqualTo(new PaymentTarget.Physical("session-1"));
        assertThat(payment.getCreatedAt()).isEqualTo(CREATED_AT);
    }
}
