package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a student tries to issue a check-in QR or check in at the door
 * without a confirmed capacity assignment for the target session (US-PHYSICAL-001,
 * escenarios 1 + 3).
 *
 * <p>The MVP deliberately distinguishes this outcome from any other "you are not
 * allowed in": a missing assignment is a <strong>product precondition</strong>
 * the student has to resolve on the web checkout, not a payment-pending status
 * (which Billing would still own as {@code PENDING_FULFILLMENT}) and not a
 * temporary technical hold (which would surface as a Redis lock failure → 409
 * {@code ALREADY_PROCESSING}). The exception is thrown inside the use case —
 * never at the controller — so the {@code @RestControllerAdvice} mapping is
 * the only place that translates it into a 403 {@code ProblemDetail}.</p>
 */
public class CapacityAssignmentRequiredException extends BusinessException {

    private static final String ERROR_CODE = "CAPACITY_ASSIGNMENT_REQUIRED";

    public CapacityAssignmentRequiredException() {
        super(ERROR_CODE, "Confirmed capacity assignment is required to check in.");
    }
}
