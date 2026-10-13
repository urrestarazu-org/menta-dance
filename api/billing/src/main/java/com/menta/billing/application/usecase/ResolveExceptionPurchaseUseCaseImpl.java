package com.menta.billing.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.menta.billing.application.contract.BillingOutboxEventTypes;
import com.menta.billing.application.dto.PurchaseResolutionResult;
import com.menta.billing.application.dto.ResolveExceptionPurchaseCommand;
import com.menta.billing.application.port.in.ResolveExceptionPurchaseUseCase;
import com.menta.billing.application.port.out.BillingOutboxAppenderPort;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.domain.exception.PurchaseNotFoundException;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.domain.model.PurchaseResolution;
import com.menta.shared.billing.PurchaseResolvedOutboxPayload;
import java.util.UUID;

/**
 * Closes an {@code EXCEPTION} purchase with an audited admin decision. The transition rules live
 * in {@link Purchase#resolve}; this class only locks the row, persists, and appends the
 * {@code billing.PurchaseResolved} outbox event in the same transaction, exactly as {@link
 * MarkPurchaseExceptionUseCase} does for the opposite transition. The payment is never touched.
 */
public class ResolveExceptionPurchaseUseCaseImpl implements ResolveExceptionPurchaseUseCase {

    private final PurchaseRepository purchaseRepository;
    private final PaymentRepository paymentRepository;
    private final BillingOutboxAppenderPort outboxAppender;
    private final Clock clock;
    private final ObjectWriter writer;

    public ResolveExceptionPurchaseUseCaseImpl(
        PurchaseRepository purchaseRepository, PaymentRepository paymentRepository,
        BillingOutboxAppenderPort outboxAppender, Clock clock
    ) {
        this.purchaseRepository = purchaseRepository;
        this.paymentRepository = paymentRepository;
        this.outboxAppender = outboxAppender;
        this.clock = clock;
        this.writer = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new ParameterNamesModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .writerFor(PurchaseResolvedOutboxPayload.class);
    }

    @Override
    public PurchaseResolutionResult resolve(ResolveExceptionPurchaseCommand command) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(command.purchaseId())
            .orElseThrow(() -> new PurchaseNotFoundException(command.purchaseId()));
        if (purchase.isResolvedAs(command.resolution(), command.note())) {
            return PurchaseResolutionResult.from(purchase);
        }
        Purchase resolved = purchaseRepository.save(
            purchase.resolve(command.resolution(), command.note(), command.adminId(), clock.now()));
        outboxAppender.append(
            BillingOutboxEventTypes.PURCHASE_RESOLVED,
            resolved.getPaymentId().getValue().toString(),
            writeJson(toPayload(resolved))
        );
        return PurchaseResolutionResult.from(resolved);
    }

    private PurchaseResolvedOutboxPayload toPayload(Purchase purchase) {
        PurchaseResolution resolution = purchase.getResolution().orElseThrow();
        UUID userId = paymentRepository.findById(purchase.getPaymentId())
            .map(Payment::getUserId)
            .orElse(null);
        return new PurchaseResolvedOutboxPayload(
            purchase.getPaymentId().getValue(), purchase.getId(), userId, resolution.type().name(),
            resolution.resolvedBy(), resolution.resolvedAt()
        );
    }

    private String writeJson(PurchaseResolvedOutboxPayload payload) {
        try {
            return writer.writeValueAsString(payload);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("PurchaseResolvedOutboxPayload JSON serialization failed", impossible);
        }
    }
}
