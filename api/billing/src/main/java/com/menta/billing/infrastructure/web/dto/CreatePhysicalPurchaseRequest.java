package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.domain.model.PaymentMethod;
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
 *
 * <p>{@code paymentMethod} accepts both {@link PaymentMethod#MERCADO_PAGO} and {@link
 * PaymentMethod#BANK_TRANSFER} (#36, US-BILLING-008): {@code
 * RoutingCreatePhysicalPurchaseCheckoutUseCase} dispatches on this same field, so a DTO-level
 * restriction to Checkout Pro only would make the bank-transfer route unreachable over HTTP
 * regardless of the router underneath — same rationale {@code CreateSubscriptionRequest} already
 * documents for its own {@code paymentMethod} (#252).</p>
 */
public record CreatePhysicalPurchaseRequest(
    @NotBlank String quoteId,
    @NotNull PaymentMethod paymentMethod,
    @NotBlank @Size(max = 128) String idempotencyKey
) {
}
