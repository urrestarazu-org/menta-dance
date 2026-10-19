package com.menta.billing.infrastructure.transaction;

import com.menta.billing.application.dto.PaymentStatusResult;
import com.menta.billing.application.port.in.GetPaymentUseCase;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for {@link GetPaymentUseCase} (#31, US-BILLING-003, design C9).
 * {@code readOnly = true} — this is a pure read, never a write boundary.
 *
 * <p>Deliberately not {@code final} — a {@code @Transactional}-adjacent class declared {@code
 * final} breaks Spring's CGLIB proxy generation (the incident documented across this change's
 * {@code tasks.md}).</p>
 */
public class TransactionalGetPaymentUseCase implements GetPaymentUseCase {

    private final GetPaymentUseCase delegate;

    public TransactionalGetPaymentUseCase(GetPaymentUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public PaymentStatusResult getPayment(String paymentId, UUID actingUserId) {
        return delegate.getPayment(paymentId, actingUserId);
    }
}
