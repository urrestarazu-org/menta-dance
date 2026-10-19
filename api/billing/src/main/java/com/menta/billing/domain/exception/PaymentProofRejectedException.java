package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * A submitted payment proof failed {@link com.menta.billing.domain.service.PaymentProofContentValidator}
 * (#31, US-BILLING-003, design C11) — declared type outside the whitelist, oversized, empty, or its
 * sniffed magic bytes disagree with the declared type. Maps to {@code 400}.
 */
public class PaymentProofRejectedException extends BusinessException {

    private static final String ERROR_CODE = "PAYMENT_PROOF_REJECTED";

    public PaymentProofRejectedException(String reason) {
        super(ERROR_CODE, reason);
    }
}
