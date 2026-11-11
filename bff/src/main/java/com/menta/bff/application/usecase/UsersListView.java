package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.UserRole;
import java.util.List;

/**
 * View model of the administrator's users list.
 *
 * @param role          role filter in force, {@code null} for every role
 * @param text          search text in force, {@code null} for none
 * @param rows          accounts of the page (empty when unavailable)
 * @param page          zero-based index of the page shown
 * @param totalPages    pages that match the filters
 * @param totalElements accounts that match the filters
 * @param unavailable   whether the accounts could not be obtained
 */
public record UsersListView(
        UserRole role, String text, List<UserRow> rows, int page, int totalPages, long totalElements,
        boolean unavailable) {

    /** The degraded view shown while the Auth API is down: the filters stay, there are no rows. */
    public static UsersListView unavailableView(UserRole role, String text) {
        return new UsersListView(role, text, List.of(), 0, 0, 0, true);
    }

    /** Whether the Auth API answered but no account matches the filters. */
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

    /** Index of the page before the one shown. */
    public int previousPage() {
        return page - 1;
    }

    /** Index of the page after the one shown. */
    public int nextPage() {
        return page + 1;
    }

    /** One-based number of the page shown, for people. */
    public int pageNumber() {
        return page + 1;
    }
}
