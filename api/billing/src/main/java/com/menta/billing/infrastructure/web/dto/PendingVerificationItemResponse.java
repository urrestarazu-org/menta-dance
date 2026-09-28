package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.PendingVerificationItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of the {@code 200} body for {@code GET /api/v1/admin/billing/payments} (design C10). */
public record PendingVerificationItemResponse(
    UUID paymentId, UUID userId, String targetModality, String targetReference, BigDecimal amount,
    String currency, String statusType, boolean hasProof, Instant createdAt
) {

    public static PendingVerificationItemResponse from(PendingVerificationItem item) {
        return new PendingVerificationItemResponse(
            item.paymentId(), item.userId(), item.targetModality(), item.targetReference(), item.amount(),
            item.currency(), item.statusType(), item.hasProof(), item.createdAt()
        );
    }
}
