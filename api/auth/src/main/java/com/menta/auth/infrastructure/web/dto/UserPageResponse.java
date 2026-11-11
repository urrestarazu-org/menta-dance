package com.menta.auth.infrastructure.web.dto;

import com.menta.auth.application.dto.UserPage;
import java.util.List;

/** Body of {@code GET /api/v1/admin/users}: one page of users, ordered by email. */
public record UserPageResponse(
    List<UserSummaryResponse> items, int page, int size, long totalElements, int totalPages
) {

    /** Maps each summary and copies the paging data, keeping the order of the items as received. */
    public static UserPageResponse from(UserPage page) {
        return new UserPageResponse(
            page.items().stream().map(UserSummaryResponse::from).toList(),
            page.page(), page.size(), page.totalElements(), page.totalPages()
        );
    }
}
