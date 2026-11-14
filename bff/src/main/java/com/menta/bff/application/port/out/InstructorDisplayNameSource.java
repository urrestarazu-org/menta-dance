package com.menta.bff.application.port.out;

import java.util.Optional;

/**
 * Port for the display name of the signed-in instructor, which the Physical API asks for as free text
 * ({@code professorName}) when an instructor creates a course (#368).
 *
 * <p>Open seam: the Auth API's access token carries only the account id and the role, and there is no
 * "current user" endpoint, so today no adapter can answer. The infrastructure default answers empty, which
 * keeps instructors from creating courses from the web until a real source (a name claim or a profile
 * endpoint) is added behind this port.
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
