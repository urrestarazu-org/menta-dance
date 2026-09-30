package com.menta.billing.application.dto;

import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;

/**
 * What a bank-transfer physical-purchase checkout returns (#36, US-BILLING-008, design C1/C2).
 *
 * <p>Deliberately <strong>not</strong> {@code PhysicalPurchaseCheckoutResult}: that record has no
 * {@code bankTransferInstructions} field yet — adding it is explicitly Phase 5's job (design C5),
 * mirroring {@code SubscriptionCheckoutResult}'s existing {@code fromBankTransfer} arm. This is a
 * phase-scoped bridge DTO that {@link
 * com.menta.billing.application.usecase.CreateBankTransferPhysicalPurchaseUseCaseImpl} returns
 * today; Phase 5 reconciles/merges this shape into {@code PhysicalPurchaseCheckoutResult} once its
 * own {@code fromBankTransfer} factory exists — this class is expected to be retired then.</p>
 *
 * <p>{@code providerPreferenceId}/{@code checkoutUrl} are omitted rather than nulled: this rail
 * never opens a Checkout Pro preference (D1), so there is nothing for those fields to ever
 * represent on this arm, unlike {@code SubscriptionCheckoutResult} which keeps both rails on one
 * shared shape.</p>
 */
public record PhysicalPurchaseBankTransferCheckoutResult(
    String paymentId, String quoteId, String status, String externalReference,
    BankTransferInstructions bankTransferInstructions
) {
    public static PhysicalPurchaseBankTransferCheckoutResult from(
        Payment payment, BankTransferInstructions bankTransferInstructions
    ) {
        PaymentTarget.Physical target = (PaymentTarget.Physical) payment.getTarget();
        return new PhysicalPurchaseBankTransferCheckoutResult(
            payment.getId().toString(), target.quoteId(), statusLabel(payment.getStatus()),
            payment.getExpectedExternalReference(), bankTransferInstructions
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
