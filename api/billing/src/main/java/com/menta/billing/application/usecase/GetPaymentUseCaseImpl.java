package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.PaymentStatusResult;
import com.menta.billing.application.port.in.GetPaymentUseCase;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.domain.exception.PaymentNotFoundException;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.Purchase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Reads a single payment's current status (#31, US-BILLING-003, design C9). {@code updatedAt} is
 * always {@link Payment#statusChangedAt()}, falling back to {@code createdAt} — never a stored
 * column, see that accessor's Javadoc.
 */
@RequiredArgsConstructor
public class GetPaymentUseCaseImpl implements GetPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final PurchaseRepository purchaseRepository;

    @Override
    public PaymentStatusResult getPayment(String paymentId, UUID actingUserId) {
        PaymentId id = PaymentId.of(paymentId);
        Payment payment = paymentRepository.findById(id)
            .orElseThrow(() -> new PaymentNotFoundException(id));
        // C8: never a 403 — a non-owner and a missing payment look identical to the caller, the
        // same anti-enumeration discipline SubmitPaymentProofUseCaseImpl already follows.
        if (!payment.getUserId().equals(actingUserId)) {
            throw new PaymentNotFoundException(id);
        }
        // #559: looked up only after the ownership check — a Purchase has no userId of its own, and
        // a non-owner must not trigger any read of it. No row (non-purchase payment, or the outbox
        // has not run yet) is a plain null, never an error.
        FulfillmentStatus fulfillment = purchaseRepository.findByPaymentId(id)
            .map(Purchase::getStatus).orElse(null);
        return new PaymentStatusResult(
            statusName(payment.getStatus()), payment.getCreatedAt(),
            payment.statusChangedAt().orElse(payment.getCreatedAt()), fulfillment
        );
    }

    private static String statusName(PaymentStatus status) {
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
