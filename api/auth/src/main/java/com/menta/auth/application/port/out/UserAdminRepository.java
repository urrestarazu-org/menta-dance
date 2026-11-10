package com.menta.auth.application.port.out;

import com.menta.auth.application.dto.ListUsersQuery;
import com.menta.auth.application.dto.UserPage;

/**
 * Administrative access to users: the paged listing.
 */
public interface UserAdminRepository {

    /**
     * Pages through the users matching the query, ordered by email and then id.
     *
     * @return the requested page; a page past the end has no items but still reports the totals
     */
    UserPage search(ListUsersQuery query);
}
