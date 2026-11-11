package com.menta.bff.application.dto;

import java.util.List;

/**
 * One page of the upstream users list ({@code UserPage} in {@code auth-v1.yaml}).
 *
 * @param items         accounts of the page; empty past the last page
 * @param page          zero-based index of the page
 * @param size          page size the upstream applied
 * @param totalElements accounts that match the query, across every page
 * @param totalPages    pages that match the query
 */
public record UserPage(List<UserSummary> items, int page, int size, long totalElements, int totalPages) {
}
