package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.domain.model.PaymentMethod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/billing/physical/purchases} (#41,
 * US-PHYSICAL-004).
 *
 * <p>No user field, by design: the purchase is always the token's owner's —
 * same discipline as {@code CreateSubscriptionRequest}. A client cannot buy
 * on someone else's behalf because there is nowhere to say so.</p>
 */
public record CreatePhysicalPurchaseRequest(
    @NotBlank String quoteId,
    @NotNull PaymentMethod paymentMethod,
    @NotBlank @Size(max = 128) String idempotencyKey
) {

    /** Same rationale as {@code CreateSubscriptionRequest}: this route implements Checkout Pro only. */
    @AssertTrue(message = "paymentMethod must be MERCADO_PAGO for Checkout Pro")
    public boolean isCheckoutProPaymentMethod() {
        return paymentMethod == PaymentMethod.MERCADO_PAGO;
    }
}
