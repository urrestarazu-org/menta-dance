package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when {@code purchaseType: MONTHLY} is requested together with a
 * {@code selectedSessionId} (US-BILLING-006) — a MONTHLY quote never fixes
 * coverage or a specific session up front.
 */
public class SelectedSessionNotAllowedException extends BusinessException {

    private static final String ERROR_CODE = "SELECTED_SESSION_NOT_ALLOWED";

    public SelectedSessionNotAllowedException() {
        super(ERROR_CODE, "selectedSessionId must not be sent for purchaseType MONTHLY");
    }
}
