package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.VirtualCourseAdminDetailView;
import java.util.UUID;

/** Inbound port for the management course-detail read (US-VIRTUAL-002 escenario 5, #125). */
public interface GetVirtualCourseAdminDetailUseCase {

    /**
     * Returns the full course (modules, lessons and {@code videoId}) in any status, only to an admin or to the
     * course's owner.
     *
     * <p>A missing or malformed id throws {@code CourseNotFoundException}; a course owned by someone else, read by
     * a non-admin, throws {@code CourseNotOwnedException}.
     */
    VirtualCourseAdminDetailView get(String courseId, UUID actingUserId, boolean actingAsAdmin);
}
