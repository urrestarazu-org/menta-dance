package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CourseDetail;

/**
 * Use case for retrieving a course's public detail projection.
 * <p>
 * Course detail is access-agnostic (design D2): it renders identically for
 * anonymous and authenticated visitors, so this use case never branches on
 * caller identity.
 * </p>
 * <p>
 * Part of Clean Architecture application layer.
 * </p>
 */
public interface GetCourseDetailUseCase {

    /**
     * Retrieves the course detail projection for the given course.
     *
     * @param courseId course identifier
     * @return course detail (modules, lessons, stats)
     * @throws com.menta.bff.application.port.out.VirtualApiClient.NotFoundException
     *         if the course does not exist or is not published (404)
     * @throws com.menta.bff.application.port.out.VirtualApiClient.ServiceUnavailableException
     *         if the upstream is unreachable (503)
     */
    CourseDetail execute(String courseId);
}
