package com.menta.bff.application.usecase;

/**
 * Use case that lists the instructors for the administrator's course form (#368). There is no paging: it
 * returns the first page of 50, the API's maximum, and says when there are more.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface ListInstructorsUseCase {

    /**
     * Lists the instructors. A failed lookup never throws: it reads as an unavailable answer.
     *
     * @param accessToken caller's access token
     * @return the instructors to choose from
     */
    InstructorOptions execute(String accessToken);
}
