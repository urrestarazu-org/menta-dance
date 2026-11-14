package com.menta.auth.application.port.in;

import com.menta.auth.application.dto.CurrentUser;
import java.util.UUID;

/** Application-layer entry point for reading the account of the authenticated user. */
public interface GetCurrentUserUseCase {

    /**
     * Returns the account the id of the access token points to.
     *
     * @param userId the id of the authenticated user, taken from the token
     * @return the user's identity, names and role
     * @throws com.menta.auth.domain.exception.UserNotFoundException when the account no longer exists although
     *     its token is still valid
     */
    CurrentUser get(UUID userId);
}
