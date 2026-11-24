package com.menta.virtual.application.dto;

/**
 * Paging, text filter and order of a library listing. The size bounds are the ones the library itself accepts
 * (10 to 1000, documented by Bunny Stream); the listing use case narrows them further for callers.
 *
 * @param page zero-based page index
 * @param size page size, between {@value #MIN_PAGE_SIZE} and {@value #MAX_PAGE_SIZE}
 * @param search text matched by the library against the title; {@code null}, empty and blank mean no filter
 * @param order ordering; {@code null} means {@link LibraryVideoOrder#DATE}
 */
public record VideoLibraryQuery(int page, int size, String search, LibraryVideoOrder order) {

    public static final int MIN_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 1000;
    public static final int MAX_SEARCH_LENGTH = 100;

    /**
     * Validates the paging and normalizes the search text and the order.
     *
     * @throws IllegalArgumentException when the page is negative, the size is outside the library bounds or the
     *     trimmed search exceeds {@value #MAX_SEARCH_LENGTH} characters
     */
    public VideoLibraryQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between " + MIN_PAGE_SIZE + " and " + MAX_PAGE_SIZE);
        }
        search = normalize(search);
        order = order == null ? LibraryVideoOrder.DATE : order;
    }

    private static String normalize(String search) {
        if (search == null) {
            return null;
        }
        String trimmed = search.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_SEARCH_LENGTH) {
            throw new IllegalArgumentException("search must not exceed " + MAX_SEARCH_LENGTH + " characters");
        }
        return trimmed;
    }
}
