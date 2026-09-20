package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.PaymentStatusResult;
import java.util.UUID;

/**
 * In-port for reading a single payment's current status (#31, US-BILLING-003, design C9, R6).
 */
public interface GetPaymentUseCase {

    /**
     * @throws com.menta.billing.domain.exception.PaymentNotFoundException if {@code paymentId}
     *     resolves to no row, or resolves to one owned by a different user (C8 — never a 403).
     */
    PaymentStatusResult getPayment(String paymentId, UUID actingUserId);
}
