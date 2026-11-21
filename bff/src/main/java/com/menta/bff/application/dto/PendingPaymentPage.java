package com.menta.bff.application.dto;

import java.util.List;

/**
 * One page of the manual-verification inbox ({@code PendingVerificationPageResponse} in
 * {@code billing-v1.yaml}).
 *
 * @param items         payments of the page, oldest first; empty past the last page
 * @param page          zero-based index of the page
 * @param size          page size the upstream applied
 * @param totalElements pending payments across every page
 * @param totalPages    pages that exist
 */
public record PendingPaymentPage(List<PendingPayment> items, int page, int size, long totalElements, int totalPages) {
}
