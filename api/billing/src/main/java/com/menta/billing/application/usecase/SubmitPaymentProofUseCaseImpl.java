package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.PaymentProofNotification;
import com.menta.billing.application.dto.RateLimitDecision;
import com.menta.billing.application.dto.SubmitPaymentProofCommand;
import com.menta.billing.application.port.in.SubmitPaymentProofUseCase;
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentProofNotificationPort;
import com.menta.billing.application.port.out.PaymentProofRepository;
import com.menta.billing.application.port.out.PaymentProofStoragePort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.domain.exception.BankTransferRateLimitedException;
import com.menta.billing.domain.exception.PaymentNotFoundException;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentProof;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.service.PaymentProofContentValidator;
import java.time.Instant;
import java.util.Optional;

/**
 * Submits or replaces a bank-transfer payment proof (#31, US-BILLING-003, design C5/C8/C12).
 *
 * <p>Follows design C12's exact write order: the upload budget is consumed only after content
 * validation passes (design C7 — a malformed file must never spend the caller's budget), the new
 * blob is stored before the database row, and the previous blob is deleted last, once the new row
 * has already committed — so a failure anywhere before that point leaves at most one orphan blob
 * that no row references, never a row that references a missing one.</p>
 */
public class SubmitPaymentProofUseCaseImpl implements SubmitPaymentProofUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentProofRepository paymentProofRepository;
    private final PaymentProofStoragePort paymentProofStoragePort;
    private final PaymentProofNotificationPort paymentProofNotificationPort;
    private final BankTransferRateLimitPort rateLimitPort;
    private final PaymentProofContentValidator validator;
    private final Clock clock;

    public SubmitPaymentProofUseCaseImpl(
        PaymentRepository paymentRepository, PaymentProofRepository paymentProofRepository,
        PaymentProofStoragePort paymentProofStoragePort, PaymentProofNotificationPort paymentProofNotificationPort,
        BankTransferRateLimitPort rateLimitPort, PaymentProofContentValidator validator, Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentProofRepository = paymentProofRepository;
        this.paymentProofStoragePort = paymentProofStoragePort;
        this.paymentProofNotificationPort = paymentProofNotificationPort;
        this.rateLimitPort = rateLimitPort;
        this.validator = validator;
        this.clock = clock;
    }

    @Override
    public void submit(SubmitPaymentProofCommand command) {
        PaymentId paymentId = PaymentId.of(command.paymentId());
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        // C8: never a 403 — a non-owner and a payment not awaiting verification look identical to
        // the caller, the same anti-enumeration discipline SubscriptionExceptionHandler documents.
        if (!payment.getUserId().equals(command.actingUserId())
            || !(payment.getStatus() instanceof PaymentStatus.AwaitingManualVerification)) {
            throw new PaymentNotFoundException(paymentId);
        }

        validator.validate(command.upload().declaredContentType(), command.upload().content());

        RateLimitDecision decision = rateLimitPort.consumeProofUpload(paymentId);
        if (!decision.isAllowed()) {
            throw new BankTransferRateLimitedException(decision.getRetryAfter());
        }

        Instant now = clock.now();
        PaymentProof newProof = PaymentProof.create(
            paymentId, command.upload().declaredContentType(), command.upload().originalFilename(),
            command.upload().sizeBytes(), now
        );

        paymentProofStoragePort.store(newProof.getStorageKey(), command.upload().content());
        Optional<String> oldKey = paymentProofRepository.findByPaymentId(paymentId).map(PaymentProof::getStorageKey);
        paymentProofRepository.save(newProof);
        paymentProofNotificationPort.notifyProofSubmitted(new PaymentProofNotification(
            paymentId.getValue(), command.actingUserId(), command.upload().originalFilename(), now
        ));
        oldKey.ifPresent(paymentProofStoragePort::delete);
    }
}
