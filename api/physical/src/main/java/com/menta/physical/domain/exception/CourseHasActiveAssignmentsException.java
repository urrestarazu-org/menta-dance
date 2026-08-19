package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when deactivating a course would strand students already assigned
 * to a future session (US-PHYSICAL-005 escenario 4).
 */
public class CourseHasActiveAssignmentsException extends BusinessException {

    private static final String ERROR_CODE = "COURSE_HAS_ACTIVE_ASSIGNMENTS";

    public CourseHasActiveAssignmentsException() {
        super(
            ERROR_CODE,
            "Course has future sessions with confirmed assignments and cannot be deactivated"
        );
    }
}
