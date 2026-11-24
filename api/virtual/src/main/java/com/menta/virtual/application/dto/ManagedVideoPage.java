package com.menta.virtual.application.dto;

import java.util.List;

/** One page of the management listing; {@code page} is zero-based. */
public record ManagedVideoPage(List<ManagedVideo> items, int page, int size, long totalItems) {

    public ManagedVideoPage {
        items = List.copyOf(items);
    }

    /** Number of pages that {@link #totalItems()} fills at {@link #size()} items each. */
    public int totalPages() {
        return size <= 0 ? 0 : (int) ((totalItems + size - 1) / size);
    }
}
