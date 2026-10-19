package com.menta.billing.application.dto;

import java.util.List;

/**
 * The admin pending-verification inbox, one page (#33, US-BILLING-005, design C10). {@code
 * items} is always oldest-first (the underlying query orders by {@code created_at ASC}).
 */
public record PendingVerificationPage(
    List<PendingVerificationItem> items, int page, int size, long totalElements, int totalPages
) {
}
