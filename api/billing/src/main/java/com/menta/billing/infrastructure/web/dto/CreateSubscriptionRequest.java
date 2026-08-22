package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.domain.model.PaymentMethod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/billing/subscriptions} (US-BILLING-010).
 *
 * <p>No user field, by design: the subscription is always the token's owner's
 * (US-BILLING-010 security NFR). A client cannot subscribe anyone else because
 * there is nowhere to say so.</p>
 */
public record CreateSubscriptionRequest(
    @NotBlank String planId,
    @NotNull PaymentMethod paymentMethod,
    @NotBlank @Size(max = 128) String idempotencyKey
) {

    /**
     * US-BILLING-010 implements the hosted Mercado Pago route only. A bank
     * transfer has a different response and verification lifecycle, and is
     * deliberately deferred to US-BILLING-003 rather than being sent to
     * Checkout Pro by mistake.
     */
    @AssertTrue(message = "paymentMethod must be MERCADO_PAGO for Checkout Pro")
    public boolean isCheckoutProPaymentMethod() {
        return paymentMethod == PaymentMethod.MERCADO_PAGO;
    }
}
