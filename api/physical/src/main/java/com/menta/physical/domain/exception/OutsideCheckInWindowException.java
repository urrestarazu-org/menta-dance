package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a check-in is attempted outside the configured per-session
 * window (US-PHYSICAL-001 escenario 4). The window is computed, not persisted:
 * {@code [session.scheduledAt - qr.sessionWindowBefore, session.scheduledAt +
 * qr.sessionWindowAfter]} per the bounds in {@code app.qr.*}. Computing it
 * keeps the {@code physical_sessions} schema lean — neither #43's update flow
 * nor the eventual #41 capacity-assignment write path needs to redo the
 * arithmetic to know whether "now" is in.
 */
public class OutsideCheckInWindowException extends BusinessException {

    private static final String ERROR_CODE = "OUTSIDE_CHECK_IN_WINDOW";

    public OutsideCheckInWindowException() {
        super(ERROR_CODE, "Check-in is not open for this session.");
    }
}
