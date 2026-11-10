package com.menta.auth.application.dto;

import com.menta.auth.domain.model.Role;

/**
 * Filter and paging for the admin user listing.
 *
 * <p>Paging is validated here so that every caller shares one contract: a page size above
 * {@value #MAX_PAGE_SIZE} is rejected, never clamped.
 *
 * @param role only users with this role, or {@code null} for every role
 * @param search text matched against email and names, trimmed; {@code null}, empty and blank all mean no filter
 * @param page zero-based page index
 * @param size page size, between 1 and {@value #MAX_PAGE_SIZE}
 */
public record ListUsersQuery(Role role, String search, int page, int size) {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    public static final int MAX_SEARCH_LENGTH = 100;

    /**
     * Validates the paging and normalizes the search text.
     *
     * @throws IllegalArgumentException when the page is negative, the size is outside 1..{@value #MAX_PAGE_SIZE}
     *     or the trimmed search exceeds {@value #MAX_SEARCH_LENGTH} characters
     */
    public ListUsersQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        search = normalize(search);
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
