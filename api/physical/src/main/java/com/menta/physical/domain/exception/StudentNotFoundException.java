package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a MANUAL check-in's {@code studentId} (#45, US-PHYSICAL-008)
 * does not resolve to an existing user via {@code UserExistencePort}. Carries
 * no field: the response must not distinguish "no such user" from "not a
 * student".
 */
public class StudentNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "STUDENT_NOT_FOUND";

    public StudentNotFoundException() {
        super(ERROR_CODE, "Student not found.");
    }
}
