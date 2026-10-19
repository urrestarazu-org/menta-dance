package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when the {@code qrCredentials} payload supplied by the door reader
 * does not parse as a well-formed token (US-PHYSICAL-001 escenario 4).
 *
 * <p>Covers three failure shapes collapsed into the same outcome, all raised
 * <strong>before</strong> any Redis lock is acquired, so a flood of malformed
 * tokens cannot create lock contention:</p>
 * <ul>
 *   <li>The payload does not start with the {@code qr:} prefix.</li>
 *   <li>It lacks one of the four expected {@code :}-separated segments.</li>
 *   <li>The embedded {@code studentId} or {@code sessionId} does not match
 *       the request's path parameters — a token bound for session A is
 *       never accepted at session B's reader.</li>
 * </ul>
 */
public class InvalidQrCredentialException extends BusinessException {

    private static final String ERROR_CODE = "INVALID_QR_CREDENTIAL";

    public InvalidQrCredentialException() {
        super(ERROR_CODE, "The QR credential is malformed or does not match the target session.");
    }
}
