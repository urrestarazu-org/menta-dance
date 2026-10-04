package com.menta.bff.application.dto;

import java.math.BigDecimal;

/**
 * Billing's answer to an in-person class checkout: a provider URL for Mercado
 * Pago, or transfer instructions for a bank transfer — exactly one of the two
 * is non-null, matching the rail that was chosen.
 *
 * @param paymentId    billing payment identifier
 * @param status       billing payment status
 * @param checkoutUrl  provider checkout URL, {@code null} for a bank transfer
 * @param bankTransfer transfer instructions, {@code null} for Mercado Pago
 */
public record ClassCheckout(String paymentId, String status, String checkoutUrl, BankTransfer bankTransfer) {

    /**
     * Where and how much to transfer, and the reference the student must quote.
     *
     * @param cbu       destination CBU
     * @param alias     destination alias
     * @param holder    account holder
     * @param cuit      holder's CUIT
     * @param amount    amount to transfer
     * @param currency  currency code
     * @param reference reference ops use to find the payment
     */
    public record BankTransfer(
            String cbu,
            String alias,
            String holder,
            String cuit,
            BigDecimal amount,
            String currency,
            String reference) {
    }
}
