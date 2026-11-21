package com.menta.bff.application.usecase;

import java.time.Instant;

/**
 * A purchase in exception as the administrator's list shows it, already worded in Spanish.
 *
 * @param purchaseId    purchase identifier, for the resolution form
 * @param studentLabel  first characters of the buyer's id: the API gives no name
 * @param modalityLabel what the purchase buys, in Spanish
 * @param amountLabel   amount and currency, es-AR formatted
 * @param createdAt     instant on which the payment was created, for the machine-readable date
 * @param createdLabel  creation date and time in the academy's zone
 * @param sessionCount  sessions left without attendance
 * @param statusLabel   fixed: the list only holds purchases in exception
 */
public record ExceptionPurchaseView(
        String purchaseId, String studentLabel, String modalityLabel, String amountLabel, Instant createdAt,
        String createdLabel, int sessionCount, String statusLabel) {
}
