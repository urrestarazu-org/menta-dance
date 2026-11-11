package com.menta.bff.application.dto;

/**
 * Query of the users list, already normalized by the use case.
 *
 * @param role filter by role, {@code null} for every role
 * @param text text to look for in email and names, {@code null} for no text filter; at most 100 characters
 * @param page zero-based page index
 * @param size page size, between 1 and 50
 */
public record UserListQuery(UserRole role, String text, int page, int size) {
}
