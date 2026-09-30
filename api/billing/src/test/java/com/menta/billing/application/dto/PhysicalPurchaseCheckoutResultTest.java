package com.menta.billing.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentTarget;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * #36, US-BILLING-008, design C5 (pulled forward into Phase 4, see apply-progress):
 * {@code PhysicalPurchaseCheckoutResult} gains the same nullable {@code bankTransferInstructions}
 * arm {@code SubscriptionCheckoutResult} already has, populated only via {@link
 * PhysicalPurchaseCheckoutResult#fromBankTransfer}. {@code providerPreferenceId}/{@code
 * checkoutUrl} stay null on that arm, mirroring the subscription precedent exactly.
 */
class PhysicalPurchaseCheckoutResultTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final Money AMOUNT = Money.of(BigDecimal.TEN, "ARS");
    private static final String QUOTE_ID = UUID.randomUUID().toString();

    private static Payment manualVerificationPayment() {
        return Payment.awaitingManualVerification(
            PaymentId.generate(), UUID.randomUUID(), AMOUNT, "PHY-BT-abc", "0000003100000000000000",
            new PaymentTarget.Physical(QUOTE_ID), NOW
        );
    }

    private static Payment awaitingProviderPayment() {
        return Payment.awaitingProvider(
            PaymentId.generate(), UUID.randomUUID(), AMOUNT, "PHY-abc", "merchant-1",
            new PaymentTarget.Physical(QUOTE_ID), NOW
        );
    }

    @Test
    void from_leaves_bank_transfer_instructions_null() {
        Payment payment = awaitingProviderPayment();
        PaymentPreferenceResult preference = new PaymentPreferenceResult("pref-1", "https://mp.example/pref-1");

        PhysicalPurchaseCheckoutResult result = PhysicalPurchaseCheckoutResult.from(payment, preference);

        assertThat(result.bankTransferInstructions()).isNull();
        assertThat(result.providerPreferenceId()).isEqualTo("pref-1");
        assertThat(result.checkoutUrl()).isEqualTo("https://mp.example/pref-1");
    }

    @Test
    void from_bank_transfer_populates_instructions_and_nulls_the_provider_fields() {
        Payment payment = manualVerificationPayment();
        BankAccountDetails account =
            new BankAccountDetails("0000003100000000000000", "menta.dance", "Menta Dance SRL", "30-00000000-0");
        BankTransferInstructions instructions =
            BankTransferInstructions.of(account, AMOUNT, payment.getExpectedExternalReference());

        PhysicalPurchaseCheckoutResult result = PhysicalPurchaseCheckoutResult.fromBankTransfer(payment, instructions);

        assertThat(result.bankTransferInstructions()).isEqualTo(instructions);
        assertThat(result.providerPreferenceId()).isNull();
        assertThat(result.checkoutUrl()).isNull();
        assertThat(result.paymentId()).isEqualTo(payment.getId().toString());
        assertThat(result.quoteId()).isEqualTo(QUOTE_ID);
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.externalReference()).isEqualTo(payment.getExpectedExternalReference());
    }
}
