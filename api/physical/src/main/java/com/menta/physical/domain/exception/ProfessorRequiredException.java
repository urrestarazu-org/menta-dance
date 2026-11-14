package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when an ADMIN-authenticated request creates a course without a {@code professorId}
 * (#743). An administrator is not a professor by default, so the course must name its owner
 * explicitly; only an INSTRUCTOR may omit it and own the course themself.
 */
public class ProfessorRequiredException extends BusinessException {

    private static final String ERROR_CODE = "PROFESSOR_REQUIRED";

    public ProfessorRequiredException() {
        super(ERROR_CODE, "professorId is required when an administrator creates a course");
    }
}
