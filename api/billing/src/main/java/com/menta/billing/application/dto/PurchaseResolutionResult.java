package com.menta.billing.application.dto;

import com.menta.billing.domain.model.Purchase;
import com.menta.billing.domain.model.PurchaseResolution;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The purchase after an admin resolution. {@code resolution}, {@code note}, {@code resolvedBy} and
 * {@code resolvedAt} are always present because only resolved purchases are returned.
 */
public record PurchaseResolutionResult(
    UUID purchaseId, UUID paymentId, String status, String resolution, String note, UUID resolvedBy,
    Instant resolvedAt, List<String> physicalSessionIds
) {

    /** Projects a resolved purchase; fails if it carries no resolution record. */
    public static PurchaseResolutionResult from(Purchase purchase) {
        PurchaseResolution resolution = purchase.getResolution().orElseThrow();
        return new PurchaseResolutionResult(
            purchase.getId(), purchase.getPaymentId().getValue(), purchase.getStatus().name(),
            resolution.type().name(), resolution.note(), resolution.resolvedBy(), resolution.resolvedAt(),
            purchase.getPhysicalSessionIds()
        );
    }
}
