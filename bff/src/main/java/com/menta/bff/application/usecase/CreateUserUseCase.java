package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.UserRole;

/**
 * Use case that creates a staff account for the administrator's users screen.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface CreateUserUseCase {

    /**
     * Creates an account that is born pending activation: the API mails its owner the activation link and
     * the owner chooses the password then. The data is trimmed here; the email keeps its case, as in the
     * public registration. A role other than the staff ones, and a blank email or name, are refused without
     * calling the API. A failure never throws: it reads as the result that names it.
     *
     * @param accessToken caller's access token
     * @param email       address of the new account, as typed
     * @param firstName   given name, as typed
     * @param lastName    family name, as typed
     * @param role        role of the new account, one of {@link UserRole#staffRoles()}
     * @return how the attempt ended, with the owner's name when it succeeded
     */
    CreateUserResult execute(String accessToken, String email, String firstName, String lastName, UserRole role);
}
