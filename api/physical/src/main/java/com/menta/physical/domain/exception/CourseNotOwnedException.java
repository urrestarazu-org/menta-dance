package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when an INSTRUCTOR attempts to manage a course they don't own
 * (US-PHYSICAL-005 escenario 5). An ADMIN never triggers this — ownership is
 * only checked for the INSTRUCTOR role.
 */
public class CourseNotOwnedException extends BusinessException {

    private static final String ERROR_CODE = "COURSE_NOT_OWNED";

    public CourseNotOwnedException() {
        super(ERROR_CODE, "You do not own this course");
    }
}
