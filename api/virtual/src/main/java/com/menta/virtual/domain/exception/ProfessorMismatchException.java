package com.menta.virtual.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when an INSTRUCTOR-authenticated request supplies a {@code
 * professorId} other than their own. Only an ADMIN may assign a course to an
 * arbitrary professor — an INSTRUCTOR creating a course is always its own
 * professor. Rejecting explicitly (rather than silently overriding) makes a
 * client's mistaken or malicious payload visible instead of masked.
 */
public class ProfessorMismatchException extends BusinessException {

    private static final String ERROR_CODE = "PROFESSOR_MISMATCH";

    public ProfessorMismatchException() {
        super(ERROR_CODE, "professorId must match the authenticated instructor");
    }
}
