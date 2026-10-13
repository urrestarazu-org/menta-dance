package com.menta.bff.application.dto;

import java.math.BigDecimal;

/**
 * The bank transfer payment started for a quote: where to send the money and the reference to quote.
 *
 * @param paymentId payment identifier (UUID text), the one the proof of transfer is uploaded to
 * @param cbu       destination account number
 * @param alias     destination account alias
 * @param holder    account holder
 * @param cuit      account holder tax id
 * @param amount    amount to transfer
 * @param currency  ISO 4217 currency of the amount
 * @param reference reference the student must include so the payment can be matched
 */
public record PhysicalBankTransfer(
        String paymentId,
        String cbu,
        String alias,
        String holder,
        String cuit,
        BigDecimal amount,
        String currency,
        String reference) {
}
