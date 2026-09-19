package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.domain.model.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/billing/subscriptions} (US-BILLING-010, US-BILLING-003).
 *
 * <p>No user field, by design: the subscription is always the token's owner's
 * (US-BILLING-010 security NFR). A client cannot subscribe anyone else because
 * there is nowhere to say so.</p>
 *
 * <p>{@code paymentMethod} accepts both {@link PaymentMethod#MERCADO_PAGO} and {@link
 * PaymentMethod#BANK_TRANSFER} (#252): {@code RoutingCreateSubscriptionCheckoutUseCase} (#31, P2)
 * dispatches on this same field, so a DTO-level restriction to Checkout Pro only would make the
 * bank-transfer route unreachable over HTTP regardless of the router underneath.</p>
 */
public record CreateSubscriptionRequest(
    @NotBlank String planId,
    @NotNull PaymentMethod paymentMethod,
    @NotBlank @Size(max = 128) String idempotencyKey
) {
}
