package com.menta.billing.application.dto;

import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;

/**
 * What the physical purchase checkout returns (#41, US-PHYSICAL-004): the
 * payment's own identifier plus the provider URL the buyer must be sent to.
 *
 * <p>Mirrors {@code SubscriptionCheckoutResult}'s three-identifier shape
 * ({@code providerPreferenceId} names the preference, {@code
 * externalReference} is ours and correlates the later webhook, the
 * provider's own {@code payment.id} does not exist yet and is therefore
 * absent). Unlike the subscription flow there is no second aggregate at
 * checkout time — {@code quoteId} stands in for it.</p>
 *
 * <p>{@code bankTransferInstructions} is the bank-transfer flow's counterpart to {@code
 * checkoutUrl}/{@code providerPreferenceId} (#36, US-BILLING-008, design C5): nullable, populated
 * only via {@link #fromBankTransfer}, mirroring {@code SubscriptionCheckoutResult}'s own {@code
 * bankTransferInstructions} field and {@code fromBankTransfer} factory exactly.</p>
 */
public record PhysicalPurchaseCheckoutResult(
    String paymentId, String quoteId, String status, String providerPreferenceId, String checkoutUrl,
    String externalReference, BankTransferInstructions bankTransferInstructions
) {
    public static PhysicalPurchaseCheckoutResult from(Payment payment, PaymentPreferenceResult preference) {
        return build(
            payment, preference.preferenceId(), preference.checkoutUrl(), null
        );
    }

    /**
     * Bank-transfer checkouts never open a Mercado Pago preference (design C5, C2) — only {@code
     * bankTransferInstructions} is populated, {@code providerPreferenceId}/{@code checkoutUrl}
     * stay null.
     */
    public static PhysicalPurchaseCheckoutResult fromBankTransfer(
        Payment payment, BankTransferInstructions bankTransferInstructions
    ) {
        return build(payment, null, null, bankTransferInstructions);
    }

    private static PhysicalPurchaseCheckoutResult build(
        Payment payment, String providerPreferenceId, String checkoutUrl,
        BankTransferInstructions bankTransferInstructions
    ) {
        PaymentTarget.Physical target = (PaymentTarget.Physical) payment.getTarget();
        return new PhysicalPurchaseCheckoutResult(
            payment.getId().toString(), target.quoteId(), statusLabel(payment.getStatus()),
            providerPreferenceId, checkoutUrl, payment.getExpectedExternalReference(), bankTransferInstructions
        );
    }

    private static String statusLabel(PaymentStatus status) {
        return switch (status) {
            case PaymentStatus.AwaitingProvider ignored -> "PENDING";
            case PaymentStatus.AwaitingManualVerification ignored -> "PENDING";
            case PaymentStatus.ReconciliationRequired ignored -> "PENDING";
            case PaymentStatus.Completed ignored -> "COMPLETED";
            case PaymentStatus.Rejected ignored -> "REJECTED";
            case PaymentStatus.Cancelled ignored -> "CANCELLED";
            case PaymentStatus.Expired ignored -> "EXPIRED";
        };
    }
}
