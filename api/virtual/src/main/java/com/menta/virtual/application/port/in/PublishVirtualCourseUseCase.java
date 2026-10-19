package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import java.util.UUID;

/** Inbound port for publishing a course (US-VIRTUAL-006). */
public interface PublishVirtualCourseUseCase {

    /**
     * Moves the course to {@code PUBLISHED} and appends a {@code PUBLISH_COURSE} audit row.
     *
     * <p>The course must have at least one module and at least one complete lesson in one of its
     * modules; otherwise {@code CourseNotPublishableException} is thrown and nothing is written.
     */
    VirtualCourseManagementResult publish(String courseId, UUID actingUserId, boolean actingAsAdmin);
}
