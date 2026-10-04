package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.CourseProgressView;
import java.util.UUID;

/**
 * Course-progress aggregate (US-VIRTUAL-005, Slice 3). Unlike {@link GetLessonProgressUseCase},
 * there is no {@code Optional} anti-enumeration shape here: an unknown, malformed, or
 * non-{@code PUBLISHED} course throws {@link com.menta.virtual.domain.exception.CourseNotFoundException}
 * directly, mirroring the write-side lesson use cases' {@code LessonNotFoundException} pattern.
 */
public interface GetCourseProgressUseCase {

    /**
     * Returns the user's progress over a course: completed and total lessons, the percentage and
     * the lesson to resume. A user without a current entitlement to the course fails with {@code
     * ForbiddenCourseProgressException}.
     */
    CourseProgressView get(String courseId, UUID actingUserId);
}
