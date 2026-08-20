package com.menta.virtual.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when publishing a course that has no module with at least one
 * complete lesson (US-VIRTUAL-006 escenario 6). Carries a human-readable
 * reason — the acceptance criteria explicitly requires indicating what is
 * missing, not just a bare error code.
 */
public class CourseNotPublishableException extends BusinessException {

    private static final String ERROR_CODE = "COURSE_NOT_PUBLISHABLE";

    public CourseNotPublishableException(String reason) {
        super(ERROR_CODE, reason);
    }
}
