package com.menta.bff.application.usecase;

/**
 * Use case that reads one course the caller manages, to prefill the edit form (#368).
 *
 * <p>Part of Clean Architecture application layer. The API has no read-one endpoint for the management
 * area, so the course is looked for among the ones it lists for the caller, which also means an instructor
 * never reads a course that is not theirs.
 * </p>
 */
public interface GetManagedCourseUseCase {

    /**
     * Reads the course.
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course as it came in the URL
     * @return the course, worded in the academy's zone
     * @throws com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException with
     *         {@code COURSE_NOT_FOUND} when the id is not a UUID, without calling the API, or when the caller's
     *         courses do not include it
     */
    ManagedCourseView execute(String accessToken, String courseId);
}
