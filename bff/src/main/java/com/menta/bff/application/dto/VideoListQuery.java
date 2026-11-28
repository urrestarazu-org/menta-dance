package com.menta.bff.application.dto;

/**
 * Query of the videos list, already normalized by the use case.
 *
 * @param search text the title must contain, {@code null} for no filter; at most 100 characters
 * @param page   zero-based page index
 * @param size   page size, between 10 and 100
 */
public record VideoListQuery(String search, int page, int size) {
}
