package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.ManagedVideoPage;
import java.util.List;

/** Body of {@code GET /api/v1/admin/virtual/videos}: one page of videos; {@code page} is zero-based. */
public record VirtualVideoPageResponse(
    List<VirtualVideoResponse> items, int page, int size, long totalElements, int totalPages
) {

    /** Maps the page and copies its paging data, keeping the order of the items as received. */
    public static VirtualVideoPageResponse from(ManagedVideoPage page) {
        return new VirtualVideoPageResponse(
            page.items().stream().map(VirtualVideoResponse::from).toList(),
            page.page(), page.size(), page.totalItems(), page.totalPages()
        );
    }
}
