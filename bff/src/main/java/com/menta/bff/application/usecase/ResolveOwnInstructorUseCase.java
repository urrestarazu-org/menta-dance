package com.menta.bff.application.usecase;

import java.util.Optional;

/**
 * Use case that tells who the signed-in instructor is, for the course creation form (#368).
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface ResolveOwnInstructorUseCase {

    /**
     * Resolves the signed-in instructor: the token's subject plus a display name. A course is never created
     * under an invented name, so without either one the answer is empty.
     *
     * @param accessToken caller's access token
     * @return the instructor, or empty when the id is not a UUID or no name is known
     */
    Optional<OwnInstructor> execute(String accessToken);
}
