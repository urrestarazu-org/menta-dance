package com.menta.bff.application.usecase;

import java.util.List;

/**
 * View model of the administrator's list of purchases in exception.
 *
 * @param rows          purchases of the page, oldest first (empty when unavailable)
 * @param page          zero-based index of the page shown
 * @param size          page size the API applied
 * @param totalPages    pages that exist
 * @param totalElements purchases in exception across every page
 * @param unavailable   whether the purchases could not be obtained
 */
public record ExceptionPurchasesView(
        List<ExceptionPurchaseView> rows, int page, int size, int totalPages, long totalElements,
        boolean unavailable) {

    /** The degraded view shown while the Billing API is down: there are no rows. */
    public static ExceptionPurchasesView unavailableView() {
        return new ExceptionPurchasesView(List.of(), 0, ManualPaymentSupport.DEFAULT_SIZE, 0, 0, true);
    }

    /** Whether the API answered but no purchase is in exception. */
    public boolean empty() {
        return !unavailable && rows.isEmpty();
    }

    /** Whether there is more than one page, so the pagination is worth showing. */
    public boolean paginated() {
        return totalPages > 1;
    }

    /** Whether a page comes before the one shown. */
    public boolean hasPrevious() {
        return page > 0;
    }

    /** Whether a page comes after the one shown. */
    public boolean hasNext() {
        return page + 1 < totalPages;
    }
}
