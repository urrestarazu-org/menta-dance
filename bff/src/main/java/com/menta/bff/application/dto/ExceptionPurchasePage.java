package com.menta.bff.application.dto;

import java.util.List;

/**
 * One page of the purchases in exception ({@code ExceptionPurchasePageResponse} in {@code billing-v1.yaml}).
 *
 * @param items         purchases of the page, oldest first; empty past the last page
 * @param page          zero-based index of the page
 * @param size          page size the upstream applied
 * @param totalElements purchases in exception across every page
 * @param totalPages    pages that exist
 */
public record ExceptionPurchasePage(
        List<ExceptionPurchase> items, int page, int size, long totalElements, int totalPages) {
}
