package com.menta.virtual.application.dto;

import java.util.List;

/**
 * One page of the library, in the order the library returned it.
 *
 * @param page zero-based index of the page
 * @param size requested page size
 * @param totalItems total number of videos matching the query across all pages
 */
public record LibraryVideoPage(List<LibraryVideo> items, int page, int size, long totalItems) {

    public LibraryVideoPage {
        items = List.copyOf(items);
    }

    /** Number of pages that {@link #totalItems()} fills at {@link #size()} items each. */
    public int totalPages() {
        return size <= 0 ? 0 : (int) ((totalItems + size - 1) / size);
    }
}
