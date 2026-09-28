package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code 200} body for {@code GET /api/v1/admin/billing/payments/{paymentId}} (#33,
 * US-BILLING-005, design C5/C6). {@code targetReference} stands in for "plan" — the payment's
 * target (subscription plan or physical course) the buyer is paying for, same shape as {@link
 * PendingVerificationItemResponse}. {@code proofUrl} is {@code null} when no proof was ever
 * submitted; otherwise it embeds a fresh 15-minute signed token minted by {@code
 * PaymentAdminController}, never constructed here — this record never sees the signing secret.
 */
public record PaymentDetailResponse(
    UUID paymentId, UUID userId, String targetModality, String targetReference, BigDecimal amount,
    String currency, String statusType, Instant createdAt, String proofUrl
) {

    public static PaymentDetailResponse from(Payment payment, String proofUrl) {
        return new PaymentDetailResponse(
            payment.getId().getValue(), payment.getUserId(), targetModality(payment.getTarget()),
            targetReference(payment.getTarget()), payment.getExpectedAmount().getAmount(),
            payment.getExpectedAmount().getCurrency(), statusType(payment.getStatus()), payment.getCreatedAt(),
            proofUrl
        );
    }

    private static String targetModality(PaymentTarget target) {
        return switch (target) {
            case PaymentTarget.Physical physical -> "PHYSICAL";
            case PaymentTarget.Virtual virtual -> "VIRTUAL";
        };
    }

    private static String targetReference(PaymentTarget target) {
        return switch (target) {
            case PaymentTarget.Physical physical -> physical.quoteId();
            case PaymentTarget.Virtual virtual -> virtual.planId();
        };
    }

    private static String statusType(PaymentStatus status) {
        return switch (status) {
            case PaymentStatus.AwaitingProvider ignored -> "AWAITING_PROVIDER";
            case PaymentStatus.AwaitingManualVerification ignored -> "AWAITING_MANUAL_VERIFICATION";
            case PaymentStatus.ReconciliationRequired ignored -> "RECONCILIATION_REQUIRED";
            case PaymentStatus.Completed ignored -> "COMPLETED";
            case PaymentStatus.Rejected ignored -> "REJECTED";
            case PaymentStatus.Cancelled ignored -> "CANCELLED";
            case PaymentStatus.Expired ignored -> "EXPIRED";
        };
    }
}
