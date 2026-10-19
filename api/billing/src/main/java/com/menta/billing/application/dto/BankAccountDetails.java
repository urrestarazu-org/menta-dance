package com.menta.billing.application.dto;

import java.util.Objects;

/**
 * The one statically configured bank account for the whole academy (design C2).
 *
 * <p>Injected via {@code @Value} into {@link
 * com.menta.billing.application.usecase.CreateBankTransferSubscriptionUseCaseImpl}'s constructor
 * — the same {@code @Value}-into-constructor shape {@code merchantAccountId} already uses for
 * {@link com.menta.billing.application.usecase.CreateSubscriptionCheckoutUseCaseImpl}. Per-plan
 * accounts are rejected (design C2): no business reason for more than one account exists today.</p>
 *
 * <p>Deliberately not blank-checked, mirroring {@code merchantAccountId}: an empty default lets
 * the application context start without the real values configured (dev/test), and production
 * profiles are expected to set the four {@code billing.bank-transfer.account.*} properties via
 * environment variables.</p>
 */
public record BankAccountDetails(String cbu, String alias, String holder, String cuit) {

    public BankAccountDetails {
        Objects.requireNonNull(cbu, "cbu cannot be null");
        Objects.requireNonNull(alias, "alias cannot be null");
        Objects.requireNonNull(holder, "holder cannot be null");
        Objects.requireNonNull(cuit, "cuit cannot be null");
    }
}
