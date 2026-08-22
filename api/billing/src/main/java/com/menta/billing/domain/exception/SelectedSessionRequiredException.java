package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when {@code purchaseType: INDIVIDUAL} is requested without a
 * {@code selectedSessionId} (US-BILLING-006) — a request-shape rule enforced
 * in the use case rather than Bean Validation, since it depends on another
 * field's value.
 */
public class SelectedSessionRequiredException extends BusinessException {

    private static final String ERROR_CODE = "SELECTED_SESSION_REQUIRED";

    public SelectedSessionRequiredException() {
        super(ERROR_CODE, "selectedSessionId is required for purchaseType INDIVIDUAL");
    }
}
