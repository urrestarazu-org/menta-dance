package com.menta.bff.application.usecase;

/**
 * Use case that lists the accounts for the administrator's users screen.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface ListUsersUseCase {

    /**
     * Lists one page of accounts, ordered by email. The filters come straight from the query string, so
     * they are normalized here: a blank or unknown role and a blank text mean no filter, the text is cut
     * to 100 characters, and a negative page reads as the first. The page size is fixed. A failed lookup
     * never throws: it reads as an unavailable view that keeps the filters.
     *
     * @param accessToken caller's access token
     * @param role        role filter as typed, may be {@code null}
     * @param text        search text as typed, may be {@code null}
     * @param page        zero-based page index, may be {@code null}
     * @return the view to render
     */
    UsersListView execute(String accessToken, String role, String text, Integer page);
}
