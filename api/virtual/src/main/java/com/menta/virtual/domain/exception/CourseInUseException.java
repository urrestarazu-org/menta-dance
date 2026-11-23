package com.menta.virtual.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when deleting a course that Billing still references (#856). Billing keeps its plans and
 * subscriptions pointing at a virtual course by value, with no foreign key, so deleting the course would
 * leave them dangling and whoever paid would lose access without any error. Historical references count:
 * a plan that is no longer active or a subscription that already expired still cites the course.
 */
public class CourseInUseException extends BusinessException {

    private static final String ERROR_CODE = "COURSE_IN_USE";

    public CourseInUseException() {
        super(ERROR_CODE, "A course referenced by Billing cannot be deleted");
    }
}
