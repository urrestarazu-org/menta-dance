package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentStatusTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");

    private static ProviderOutcome outcome(String providerStatus) {
        return new ProviderOutcome(providerStatus, Money.of(BigDecimal.TEN, "ARS"), "ext-1", "merchant-1");
    }

    @Test
    void resolves_approved_to_completed() {
        assertThat(new PaymentStatus.AwaitingProvider().resolve(outcome("approved"), NOW))
            .isEqualTo(new PaymentStatus.Completed(NOW));
    }

    @Test
    void resolves_rejected_to_rejected() {
        assertThat(new PaymentStatus.AwaitingProvider().resolve(outcome("rejected"), NOW))
            .isEqualTo(new PaymentStatus.Rejected(NOW));
    }

    @Test
    void resolves_cancelled_to_cancelled() {
        assertThat(new PaymentStatus.AwaitingProvider().resolve(outcome("cancelled"), NOW))
            .isEqualTo(new PaymentStatus.Cancelled(NOW));
    }

    @Test
    void resolves_expired_to_expired() {
        assertThat(new PaymentStatus.AwaitingProvider().resolve(outcome("expired"), NOW))
            .isEqualTo(new PaymentStatus.Expired(NOW));
    }

    @Test
    void resolves_pending_and_in_process_to_awaiting_provider() {
        assertThat(new PaymentStatus.AwaitingProvider().resolve(outcome("pending"), NOW))
            .isEqualTo(new PaymentStatus.AwaitingProvider());
        assertThat(new PaymentStatus.AwaitingProvider().resolve(outcome("in_process"), NOW))
            .isEqualTo(new PaymentStatus.AwaitingProvider());
    }

    @Test
    void resolves_unknown_status_to_reconciliation_required() {
        assertThat(new PaymentStatus.AwaitingProvider().resolve(outcome("charged_back"), NOW))
            .isEqualTo(new PaymentStatus.ReconciliationRequired("unknown provider status: charged_back"));
    }

    @Test
    void reconciliation_required_can_resolve_again_on_a_later_matching_webhook() {
        PaymentStatus.ReconciliationRequired stuck = new PaymentStatus.ReconciliationRequired("mismatch");

        assertThat(stuck.resolve(outcome("approved"), NOW)).isEqualTo(new PaymentStatus.Completed(NOW));
    }

    @Test
    void awaiting_manual_verification_resolves_the_same_way() {
        assertThat(new PaymentStatus.AwaitingManualVerification().resolve(outcome("approved"), NOW))
            .isEqualTo(new PaymentStatus.Completed(NOW));
    }
}
