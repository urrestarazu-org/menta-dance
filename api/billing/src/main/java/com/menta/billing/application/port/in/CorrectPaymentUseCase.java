package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.CorrectPaymentCommand;

/**
 * Admin exceptional correction of a bank-transfer payment stuck in {@code
 * ReconciliationRequired} (#33, US-BILLING-005, design D9).
 */
public interface CorrectPaymentUseCase {

    void correct(CorrectPaymentCommand command);
}
