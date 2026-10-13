package com.menta.bff.application.usecase;

/**
 * View model for the bank transfer instructions page.
 *
 * @param paymentId   payment the proof of transfer is uploaded to
 * @param cbu         destination account number
 * @param alias       destination account alias
 * @param holder      account holder
 * @param cuit        account holder tax id
 * @param amountLabel amount formatted for es-AR with its currency
 * @param reference   reference to include in the transfer
 */
public record BankTransferInstructionsView(
        String paymentId, String cbu, String alias, String holder, String cuit, String amountLabel,
        String reference) {
}
