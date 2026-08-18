package com.menta.billing.application.dto;

import com.menta.billing.domain.model.Money;

/** Minimal shape {@code PaymentProviderPort} returns — never the full provider payload (ADR-0038). */
public record ProviderPaymentResult(
    String providerStatus, Money amount, String externalReference, String merchantAccountId
) {
}
