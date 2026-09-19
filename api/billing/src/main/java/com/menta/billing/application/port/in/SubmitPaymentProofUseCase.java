package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.SubmitPaymentProofCommand;

/**
 * In-port for submitting (or replacing) a bank-transfer payment proof (#31, US-BILLING-003,
 * design C5/C12).
 */
public interface SubmitPaymentProofUseCase {

    void submit(SubmitPaymentProofCommand command);
}
