package com.menta.billing.application.dto;

import java.util.List;

/**
 * The admin EXCEPTION-purchases inbox, one page (#237, design C5). {@code items} is always
 * oldest-first (the underlying query orders by the joined {@code Payment.createdAt ASC}).
 */
public record ExceptionPurchasePage(
    List<ExceptionPurchaseItem> items, int page, int size, long totalElements, int totalPages
) {
}
