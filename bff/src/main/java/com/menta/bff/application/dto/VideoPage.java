package com.menta.bff.application.dto;

import java.util.List;

/**
 * One page of the videos list ({@code VirtualVideoPageResponse} in {@code virtual-v1.yaml}).
 *
 * @param items         videos of the page; empty past the last page
 * @param page          zero-based index of the page
 * @param size          page size the API applied
 * @param totalElements videos that match the query, across every page
 * @param totalPages    pages that match the query
 */
public record VideoPage(List<ManagedVideo> items, int page, int size, long totalElements, int totalPages) {
}
