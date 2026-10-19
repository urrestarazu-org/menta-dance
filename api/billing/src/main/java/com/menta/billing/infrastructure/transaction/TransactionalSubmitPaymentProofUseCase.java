package com.menta.billing.infrastructure.transaction;

import com.menta.billing.application.dto.SubmitPaymentProofCommand;
import com.menta.billing.application.port.in.SubmitPaymentProofUseCase;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator for {@link SubmitPaymentProofUseCase} (#31, US-BILLING-003, design
 * C12). The proof row, the notification and the previous blob's deletion must commit or roll back
 * together with the payment read — a failed notification (a {@code MailException}, design C12 step
 * 6) rolls back the whole submission so the caller retries within their upload budget instead of
 * being left with a persisted row nobody was told about.
 *
 * <p>Deliberately not {@code final} — a {@code @Transactional}-adjacent class declared {@code
 * final} breaks Spring's CGLIB proxy generation (the incident documented across this change's
 * {@code tasks.md}).</p>
 */
public class TransactionalSubmitPaymentProofUseCase implements SubmitPaymentProofUseCase {

    private final SubmitPaymentProofUseCase delegate;

    public TransactionalSubmitPaymentProofUseCase(SubmitPaymentProofUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public void submit(SubmitPaymentProofCommand command) {
        delegate.submit(command);
    }
}
