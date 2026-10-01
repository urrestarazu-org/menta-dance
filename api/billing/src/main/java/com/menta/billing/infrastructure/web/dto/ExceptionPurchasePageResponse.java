package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.ExceptionPurchaseItem;
import com.menta.billing.application.dto.ExceptionPurchasePage;
import java.util.List;

/**
 * {@code 200} body for {@code GET /api/v1/admin/billing/purchases?status=EXCEPTION} (#237, design
 * C5/C6). Items are always oldest-first; {@code physicalSessionIds} is {@code []}, never {@code
 * null}, for a zero-session row (D7). Every field of {@link ExceptionPurchaseItem} is already
 * JSON-serializable, so it is reused directly as the response item shape — no {@code reason} field
 * and no buyer email are ever exposed (scope discipline, D-out-of-scope).
 */
public record ExceptionPurchasePageResponse(
    List<ExceptionPurchaseItem> items, int page, int size, long totalElements, int totalPages
) {

    public static ExceptionPurchasePageResponse from(ExceptionPurchasePage page) {
        return new ExceptionPurchasePageResponse(
            page.items(), page.page(), page.size(), page.totalElements(), page.totalPages()
        );
    }
}
