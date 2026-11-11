package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.CreateUserCommand;
import com.menta.bff.application.dto.UserListQuery;
import com.menta.bff.application.dto.UserPage;
import com.menta.bff.application.dto.UserRole;
import com.menta.bff.application.dto.UserSummary;

/**
 * Port for the administrator's accounts endpoints of the Auth API ({@code /api/v1/admin/users}): the list
 * ({@code GET}), the creation of staff accounts ({@code POST}) and the change of an account's role
 * ({@code PATCH /{userId}/role}).
 *
 * <p>The calls are authenticated with the administrator's access token, which only ever travels in the
 * {@code Authorization} header: it is never logged nor put in an exception message. Neither are the
 * request parameters (they carry search text typed by the administrator), the data of the account being
 * created or changed or the response, which holds emails and names.
 * </p>
 */
public interface AdminUsersApiClient {

    /**
     * Lists accounts, ordered by email ({@code GET /api/v1/admin/users}).
     *
     * @param accessToken caller's access token
     * @param query       normalized filters and paging
     * @return the requested page; past the last page it has no items but carries the totals
     * @throws InvalidRequestException     if the upstream rejects the query (400)
     * @throws ServiceUnavailableException for any other failure: timeout, transport error, 401, 403,
     *                                     429, 5xx, an unexpected status or an unusable body
     */
    UserPage listUsers(String accessToken, UserListQuery query);

    /**
     * Creates a staff account ({@code POST /api/v1/admin/users}). The account is born pending activation and
     * the API mails its owner the activation link, so there is nothing to return: a {@code 201} is the whole
     * answer, and its body is never read (the account exists even if it were unreadable).
     *
     * @param accessToken caller's access token
     * @param command     email, names and staff role of the new account
     * @throws EmailAlreadyRegisteredException if an account with that email exists (409
     *                                         {@code EMAIL_ALREADY_REGISTERED})
     * @throws InvalidRequestException         if the upstream rejects the data (400)
     * @throws ServiceUnavailableException     for any other failure: timeout, transport error, 401, 403, 404,
     *                                         429, 5xx, any other 409, or any status but 201
     */
    void createUser(String accessToken, CreateUserCommand command);

    /**
     * Gives an account another role ({@code PATCH /api/v1/admin/users/{userId}/role}). The API closes every
     * session of that account when the role really changes; asking for the role it already has is an
     * idempotent no-op that also answers {@code 200}. The identifier travels as one encoded path segment.
     *
     * @param accessToken caller's access token
     * @param userId      identifier of the account, already validated as a UUID by the caller
     * @param role        the role to assign
     * @return the account as it is after the call
     * @throws UserNotFoundException              if the account does not exist (404 {@code USER_NOT_FOUND})
     * @throws LastAdminCannotBeDemotedException  if the change would leave no active administrator (409
     *                                            {@code LAST_ADMIN_CANNOT_BE_DEMOTED})
     * @throws InvalidRequestException            if the upstream rejects the request (400)
     * @throws ServiceUnavailableException       for any other failure: timeout, transport error, 401, 403,
     *                                            429, 5xx, any other 404 or 409, any status but 200, or a
     *                                            200 without a usable account. The call is idempotent, so
     *                                            repeating it after the last case is harmless
     */
    UserSummary changeRole(String accessToken, String userId, UserRole role);

    /** An account with that email already exists (409 {@code EMAIL_ALREADY_REGISTERED}). */
    class EmailAlreadyRegisteredException extends RuntimeException {
        public EmailAlreadyRegisteredException(String message) {
            super(message);
        }
    }

    /** The account does not exist (404 {@code USER_NOT_FOUND}). */
    class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }

    /** The change would leave the system without an active administrator (409 {@code LAST_ADMIN_CANNOT_BE_DEMOTED}). */
    class LastAdminCannotBeDemotedException extends RuntimeException {
        public LastAdminCannotBeDemotedException(String message) {
            super(message);
        }
    }

    /** The upstream rejected the request as invalid (400). Its message is for logs only. */
    class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }

    /** The users could not be obtained. Its message is for logs only. */
    class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }

        public ServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
