package com.menta.virtual.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when an authenticated caller without a current course entitlement asks for the
 * course-progress aggregate (US-VIRTUAL-005, Slice 3). Unlike {@link ForbiddenLessonAccessException},
 * there is no free/preview exception here (design.md decision 5): the course was already resolved
 * as {@code PUBLISHED} before this is thrown, so it never leaks anything the public catalog does not.
 */
public class ForbiddenCourseProgressException extends BusinessException {

    private static final String ERROR_CODE = "COURSE_PROGRESS_FORBIDDEN_SUBSCRIPTION_REQUIRED";

    public ForbiddenCourseProgressException() {
        super(ERROR_CODE, "This course's progress requires an authenticated subscription.");
    }
}
