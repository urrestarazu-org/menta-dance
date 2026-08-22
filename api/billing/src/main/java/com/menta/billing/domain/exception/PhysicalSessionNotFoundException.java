package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when an INDIVIDUAL quote's {@code selectedSessionId} is not among
 * the course's {@code SCHEDULED} sessions in the quoted calendar-month
 * period (US-BILLING-006 escenario 2).
 */
public class PhysicalSessionNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "PHYSICAL_SESSION_NOT_FOUND";

    public PhysicalSessionNotFoundException() {
        super(ERROR_CODE, "Selected session not found in the quoted period");
    }
}
