package com.menta.bff.domain.membership;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One price tier of the membership: the monthly installment that applies to everyone who subscribes up to
 * {@code deadline}.
 *
 * @param label        text of the "Alta hasta" column (for example {@code "16 de noviembre 2026"})
 * @param price        monthly installment in Argentine pesos
 * @param installments number of installments the subscription charges
 * @param deadline     last day (inclusive, Argentina time) on which this tier can be subscribed to
 * @param payUrl       Mercado Pago subscription link of this tier
 * @param exclusive    whether the tier is the benefit reserved for participants with perfect attendance
 */
public record PriceTier(
        String label, int price, int installments, LocalDate deadline, String payUrl, boolean exclusive) {

    /** Validates the tier invariants. */
    public PriceTier {
        if (price <= 0) {
            throw new IllegalArgumentException("price must be positive: " + price);
        }
        if (installments <= 0) {
            throw new IllegalArgumentException("installments must be positive: " + installments);
        }
        Objects.requireNonNull(deadline, "deadline cannot be null");
    }
}
