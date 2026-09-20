package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.ResolvePaymentProofCommand;

/** Admin approve/reject of a bank-transfer payment proof (#31, US-BILLING-003, design D1). */
public interface ResolvePaymentProofUseCase {

    void resolve(ResolvePaymentProofCommand command);
}
