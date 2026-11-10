package com.menta.auth.application.port.in;

import com.menta.auth.application.dto.ListUsersQuery;
import com.menta.auth.application.dto.UserPage;

/** Application-layer entry point for the admin user listing. */
public interface ListUsersUseCase {

    /** Returns one page of users matching the filter, ordered by email and then id. */
    UserPage list(ListUsersQuery query);
}
