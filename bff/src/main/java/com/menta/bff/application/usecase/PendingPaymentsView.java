package com.menta.bff.application.usecase;

import java.util.List;

/**
 * View model of the administrator's inbox of payments awaiting verification.
 *
 * @param rows          payments of the page, oldest first (empty when unavailable)
 * @param page          zero-based index of the page shown
 * @param size          page size the API applied
 * @param totalPages    pages that exist
 * @param totalElements pending payments across every page
 * @param unavailable   whether the payments could not be obtained
 */
public record PendingPaymentsView(
        List<PaymentVerificationView> rows, int page, int size, int totalPages, long totalElements,
        boolean unavailable) {

    /** The degraded view shown while the Billing API is down: there are no rows. */
    public static PendingPaymentsView unavailableView() {
        return new PendingPaymentsView(List.of(), 0, ListPendingPaymentsUseCase.DEFAULT_SIZE, 0, 0, true);
    }

    /** Whether the API answered but nothing awaits verification. */
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
