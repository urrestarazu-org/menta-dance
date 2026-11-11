package com.menta.auth.application.usecase;

import com.menta.auth.application.dto.ListUsersQuery;
import com.menta.auth.application.dto.UserPage;
import com.menta.auth.application.port.in.ListUsersUseCase;
import com.menta.auth.application.port.out.UserAdminRepository;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link ListUsersUseCase}: a read-only query, so it needs no transaction of its own. */
@RequiredArgsConstructor
public class ListUsersUseCaseImpl implements ListUsersUseCase {

    private final UserAdminRepository userAdminRepository;

    @Override
    public UserPage list(ListUsersQuery query) {
        return userAdminRepository.search(query);
    }
}
