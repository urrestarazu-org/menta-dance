package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.CreatePhysicalPurchaseCheckoutCommand;
import com.menta.billing.application.dto.PhysicalPurchaseCheckoutResult;

/** Entry port for {@code POST /api/v1/billing/physical/purchases} (#41, US-PHYSICAL-004). */
public interface CreatePhysicalPurchaseCheckoutUseCase {

    /**
     * Opens a Checkout Pro purchase for a physical-course quote.
     *
     * <p>Only {@code MERCADO_PAGO} is accepted; any other method fails with {@link
     * IllegalArgumentException}. The payment's reference is derived deterministically from the user
     * and the idempotency key, so a replay finds the existing payment instead of creating a second
     * one, although it still asks the provider for a checkout link. An unknown or expired quote
     * fails with {@code PhysicalCourseQuoteExpiredException}. A fresh purchase holds the capacity
     * of the quoted sessions before the payment is saved, and a provider failure releases that hold
     * and surfaces as {@code PaymentPreferenceUnavailableException}.
     */
    PhysicalPurchaseCheckoutResult create(CreatePhysicalPurchaseCheckoutCommand command);
}
