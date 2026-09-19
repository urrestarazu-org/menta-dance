package com.menta.billing.application.dto;

import com.menta.billing.domain.model.Money;
import java.util.Objects;

/**
 * What a bank-transfer checkout returns instead of a Checkout Pro redirect URL
 * (US-BILLING-003 escenario 1): the account to transfer to, the amount, and the reference the
 * student must quote so ops can find the row.
 *
 * <p>{@code reference} is {@code SUB-{paymentId}} — the same {@code EXTERNAL_REFERENCE_PREFIX}
 * value {@code CreateSubscriptionCheckoutUseCaseImpl} already generates for Checkout Pro (design
 * C2), already carrying {@code uq_billing_payments_external_reference}.</p>
 */
public record BankTransferInstructions(
    String cbu, String alias, String holder, String cuit, Money amount, String reference
) {

    public BankTransferInstructions {
        Objects.requireNonNull(cbu, "cbu cannot be null");
        Objects.requireNonNull(alias, "alias cannot be null");
        Objects.requireNonNull(holder, "holder cannot be null");
        Objects.requireNonNull(cuit, "cuit cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(reference, "reference cannot be null");
    }

    public static BankTransferInstructions of(BankAccountDetails account, Money amount, String reference) {
        return new BankTransferInstructions(
            account.cbu(), account.alias(), account.holder(), account.cuit(), amount, reference
        );
    }
}
