package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.domain.model.PurchaseResolutionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/admin/billing/purchases/{purchaseId}/resolution} (#539).
 *
 * <p>The note is the audit record of why the purchase was closed, so it is mandatory; bean
 * validation rejects a blank, absent or over-long value with {@code 400} before the use case runs.
 *
 * @param resolution how the purchase was closed: {@code REFUNDED} or {@code COMPENSATED}.
 * @param note free-text justification, at most 500 characters.
 */
public record ResolveExceptionPurchaseRequest(
    @NotNull PurchaseResolutionType resolution,
    @NotBlank @Size(max = 500) String note
) {
}
