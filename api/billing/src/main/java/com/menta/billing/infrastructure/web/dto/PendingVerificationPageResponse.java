package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.PendingVerificationPage;
import java.util.List;

/**
 * {@code 200} body for {@code GET /api/v1/admin/billing/payments} (#33, US-BILLING-005, design
 * C10). Items are always oldest-first.
 */
public record PendingVerificationPageResponse(
    List<PendingVerificationItemResponse> items, int page, int size, long totalElements, int totalPages
) {

    public static PendingVerificationPageResponse from(PendingVerificationPage page) {
        return new PendingVerificationPageResponse(
            page.items().stream().map(PendingVerificationItemResponse::from).toList(),
            page.page(), page.size(), page.totalElements(), page.totalPages()
        );
    }
}
