package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a physical course has no {@code SCHEDULED} session in the
 * quoted calendar-month period (US-BILLING-006 escenario 3) — no quote is
 * ever persisted for an unpayable period.
 */
public class NoScheduledSessionsException extends BusinessException {

    private static final String ERROR_CODE = "NO_SCHEDULED_SESSIONS";

    public NoScheduledSessionsException() {
        super(ERROR_CODE, "No scheduled sessions in the quoted period");
    }
}
