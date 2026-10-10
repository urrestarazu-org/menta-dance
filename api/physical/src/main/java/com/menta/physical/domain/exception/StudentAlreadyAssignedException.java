package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a student is assigned to a session they already hold a confirmed spot in
 * ({@code UNIQUE (session_id, student_id)}). Distinct from {@link CapacityBelowAssignedException}:
 * the session may have spare capacity, the student simply already has the session.
 */
public class StudentAlreadyAssignedException extends BusinessException {

    private static final String ERROR_CODE = "STUDENT_ALREADY_ASSIGNED";

    public StudentAlreadyAssignedException() {
        super(ERROR_CODE, "Student is already assigned to this session");
    }
}
