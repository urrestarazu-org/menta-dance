package com.menta.billing.domain.model;

import java.util.Objects;

/**
 * A payment result as reported by the provider (Mercado Pago) for a single
 * {@code data.id} query — raw provider status plus the fields {@link Payment}
 * must match exactly before trusting it (US-BILLING-002: "para todo estado
 * del proveedor, coincidencia exacta con la compra esperada").
 *
 * @param providerStatus raw status string from the provider (e.g. {@code
 *     "approved"}, {@code "rejected"}, {@code "pending"}) — never
 *     interpreted here, only in {@link PaymentStatus.Pending#resolve}.
 */
public record ProviderOutcome(
    String providerStatus, Money amount, String externalReference, String merchantAccountId
) {
    public ProviderOutcome {
        Objects.requireNonNull(providerStatus, "providerStatus cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(externalReference, "externalReference cannot be null");
        Objects.requireNonNull(merchantAccountId, "merchantAccountId cannot be null");
    }
}
