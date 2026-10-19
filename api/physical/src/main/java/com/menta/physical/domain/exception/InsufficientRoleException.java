package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a MANUAL check-in (#45, US-PHYSICAL-008) is attempted by a
 * caller who is not authenticated as {@code RECEPTIONIST} or {@code ADMIN} —
 * including an anonymous caller. Carries no field: the response must not
 * echo which roles would have been accepted.
 */
public class InsufficientRoleException extends BusinessException {

    private static final String ERROR_CODE = "INSUFFICIENT_ROLE";

    public InsufficientRoleException() {
        super(ERROR_CODE, "Manual check-in requires the RECEPTIONIST or ADMIN role.");
    }
}
