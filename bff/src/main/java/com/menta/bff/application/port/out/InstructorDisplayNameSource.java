package com.menta.bff.application.port.out;

import java.util.Optional;

/**
 * Port for the display name of the signed-in instructor, which the Physical API asks for as free text
 * ({@code professorName}) when an instructor creates a course (#368).
 *
 * <p>The Auth API's access token carries only the account id and the role, so the name is read from the
 * Auth API's current-user endpoint ({@code GET /api/v1/users/me}, #749). An answer of empty (an account
 * without a name, or an Auth API that cannot answer) keeps the instructor from creating a course from the web
 * rather than sending a made-up name.
 * </p>
 */
public interface InstructorDisplayNameSource {

    /**
     * Returns the signed-in instructor's display name.
     *
     * @param accessToken caller's access token
     * @return the name, or empty when it cannot be known
     */
    Optional<String> displayNameOf(String accessToken);
}
