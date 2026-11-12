package com.menta.bff.application.usecase;

/**
 * Use case that gives an account another role, for the administrator's users screen.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface ChangeUserRoleUseCase {

    /**
     * Changes the role of an account. The API closes every session of that account when the role really
     * changes. Anything that can never be sent is refused without calling the API: a missing access token, an
     * id that is not a UUID (it becomes a path segment) and a role that is missing or is not one of the four
     * roles. A failure never throws: it reads as the result that names it.
     *
     * @param accessToken caller's access token
     * @param userId      identifier of the account, as submitted
     * @param role        name of the role to assign, as submitted
     * @return how the attempt ended; {@link ChangeUserRoleResult.Outcome#CHANGED_OWN_ROLE} when the account is
     *         the caller's own and its role really changed, which ends the caller's own session
     */
    ChangeUserRoleResult execute(String accessToken, String userId, String role);
}
