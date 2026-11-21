package com.menta.bff.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One purchase stranded in {@code EXCEPTION} ({@code ExceptionPurchaseItemResponse} in {@code billing-v1.yaml}).
 * The API gives neither a state nor a reason: the list only holds purchases in exception.
 *
 * @param purchaseId         purchase identifier
 * @param userId             identifier of the buyer
 * @param targetModality     whether it buys a physical course or a virtual plan
 * @param amount             amount paid
 * @param currency           ISO 4217 currency code of the amount
 * @param createdAt          instant on which the payment was created, in UTC
 * @param physicalSessionIds sessions the exception left without attendance; empty when it had none
 */
public record ExceptionPurchase(
        String purchaseId,
        String userId,
        PaymentModality targetModality,
        BigDecimal amount,
        String currency,
        Instant createdAt,
        List<String> physicalSessionIds) {
}
