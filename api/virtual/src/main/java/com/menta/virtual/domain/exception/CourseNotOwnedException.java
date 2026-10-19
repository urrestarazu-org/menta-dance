package com.menta.virtual.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when an INSTRUCTOR attempts to manage a course (or its modules or
 * lessons) they don't own. An ADMIN never triggers this.
 */
public class CourseNotOwnedException extends BusinessException {

    private static final String ERROR_CODE = "COURSE_NOT_OWNED";

    public CourseNotOwnedException() {
        super(ERROR_CODE, "You do not own this course");
    }
}
