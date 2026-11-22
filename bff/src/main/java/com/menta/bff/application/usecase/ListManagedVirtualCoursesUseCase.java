package com.menta.bff.application.usecase;

/**
 * Use case that lists the virtual courses the caller manages (#367): all of them for an administrator, the own
 * ones for an instructor. The API does not page this list.
 *
 * <p>Part of Clean Architecture application layer. Failures of the API reach the caller as
 * {@code VirtualCourseManagementApiClient.ServiceUnavailableException}, so the screen shows
 * {@link ManagedVirtualCoursesView#unavailableView()}.
 * </p>
 */
public interface ListManagedVirtualCoursesUseCase {

    /**
     * Lists the courses.
     *
     * @param accessToken caller's access token
     * @return the courses worded for the screen, in the order of the API, possibly empty
     */
    ManagedVirtualCoursesView execute(String accessToken);
}
