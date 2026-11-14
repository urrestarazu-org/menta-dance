package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CreateCourseCommand;

/**
 * Use case that creates a physical course from the management form (#368). The weekday and time arrive in the
 * academy's zone and are sent to the API in UTC (ADR-0048); the instructor is always sent explicitly.
 *
 * <p>Part of Clean Architecture application layer. Business rules (who may assign which instructor) are the
 * API's: its refusals reach the caller as {@code CourseManagementException}.
 * </p>
 */
public interface CreateManagedCourseUseCase {

    /**
     * Creates the course.
     *
     * @param accessToken caller's access token
     * @param command     the course; its {@code professorId} is the administrator's choice or the instructor's own id
     * @return the course as created
     * @throws com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException with
     *         {@code PROFESSOR_REQUIRED} when {@code professorId} is missing or not a UUID, without calling the
     *         API, or with the API's own refusal
     */
    ManagedCourseView execute(String accessToken, CreateCourseCommand command);
}
