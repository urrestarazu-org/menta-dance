package com.menta.billing.application.dto;

import com.menta.billing.domain.model.PurchaseResolutionType;
import java.util.UUID;

/** An admin's decision closing a purchase in {@code EXCEPTION}; {@code adminId} feeds the audit record. */
public record ResolveExceptionPurchaseCommand(
    UUID purchaseId, PurchaseResolutionType resolution, String note, UUID adminId
) {
}
