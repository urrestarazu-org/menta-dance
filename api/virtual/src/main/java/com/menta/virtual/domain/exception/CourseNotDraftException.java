package com.menta.virtual.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when deleting a course that is not {@code DRAFT} (US-VIRTUAL-006:
 * "Eliminar curso (solo draft)"). A course that was never published cannot
 * have any subscriber by construction, so this restriction alone already
 * satisfies "no eliminar cursos con suscriptores activos" — no separate
 * cross-module check against Billing is needed.
 */
public class CourseNotDraftException extends BusinessException {

    private static final String ERROR_CODE = "COURSE_NOT_DRAFT";

    public CourseNotDraftException() {
        super(ERROR_CODE, "Only a DRAFT course can be deleted");
    }
}
