package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.UserListQuery;
import com.menta.bff.application.dto.UserPage;

/**
 * Port for the administrator's accounts endpoint of the Auth API ({@code GET /api/v1/admin/users}).
 *
 * <p>The call is authenticated with the administrator's access token, which only ever travels in the
 * {@code Authorization} header: it is never logged nor put in an exception message. Neither are the
 * request parameters (they carry search text typed by the administrator) or the response, which holds
 * emails and names.
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

    /** The upstream rejected the query as invalid (400). Its message is for logs only. */
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
