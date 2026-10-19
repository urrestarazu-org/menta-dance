package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when the door-side Redis lock acquisition fails because another
 * reader is already processing the same {@code (sessionId, userId)} pair
 * (US-PHYSICAL-001 escenario 6). The lock has no compare-and-delete
 * compensation — it is intentional: the MVP bounds the damage by an inventory
 * TTL and lets the in-flight INSERT either commit or expire on its own
 * rather than spending a two-phase commit on a hallway scan.
 *
 * <p>Surfacing this as {@code 409 ALREADY_PROCESSING} tells the reader firmware
 * the request is still in flight and to back off briefly, not that it failed
 * categorically. The next scan of the same QR within the lock TTL will
 * either resolve idempotently (row already inserted → 200 on escenario 5)
 * or free the lock, so the reader can retry.</p>
 */
public class CheckInAlreadyProcessingException extends BusinessException {

    private static final String ERROR_CODE = "ALREADY_PROCESSING";

    public CheckInAlreadyProcessingException() {
        super(ERROR_CODE, "Another reader is currently processing this check-in.");
    }
}
