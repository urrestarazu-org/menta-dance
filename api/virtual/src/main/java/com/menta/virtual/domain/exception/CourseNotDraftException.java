package com.menta.virtual.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when deleting a course that is not {@code DRAFT} (US-VIRTUAL-006:
 * "Eliminar curso (solo draft)"). The status alone does not rule out student
 * history, because a published course can go back to {@code DRAFT}: see
 * {@link CourseHasProgressException}.
 */
public class CourseNotDraftException extends BusinessException {

    private static final String ERROR_CODE = "COURSE_NOT_DRAFT";

    public CourseNotDraftException() {
        super(ERROR_CODE, "Only a DRAFT course can be deleted");
    }
}
