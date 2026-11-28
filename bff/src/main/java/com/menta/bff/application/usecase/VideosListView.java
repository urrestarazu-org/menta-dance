package com.menta.bff.application.usecase;

import java.util.List;

/**
 * View model of the videos list the caller manages (#842).
 *
 * @param search        search text in force, {@code null} for none
 * @param rows          videos of the page (empty when unavailable)
 * @param page          zero-based index of the page shown
 * @param totalPages    pages that match the search
 * @param totalElements videos that match the search
 * @param unavailable   whether the videos could not be obtained
 * @param libraryDown   whether the API said that the library itself did not answer (implies {@code unavailable})
 */
public record VideosListView(
        String search, List<VideoRow> rows, int page, int totalPages, long totalElements, boolean unavailable,
        boolean libraryDown) {

    /** The degraded view shown while the API or the library is down: the search stays, there are no rows. */
    public static VideosListView unavailableView(String search, boolean libraryDown) {
        return new VideosListView(search, List.of(), 0, 0, 0, true, libraryDown);
    }

    /** Whether the API answered but no video matches. */
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
