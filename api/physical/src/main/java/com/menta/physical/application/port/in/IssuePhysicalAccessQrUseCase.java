package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.AccessQrView;
import java.util.UUID;

/**
 * Entry point the authenticated student app calls to obtain an ephemeral
 * check-in QR for a session it is confirmed to attend (US-PHYSICAL-001
 * escenario 1). {@code POST /api/v1/physical/sessions/{sessionId}/access-qr}
 * requires an authenticated caller but no specific role — the student's own
 * identity is the only authorization this endpoint needs.
 */
public interface IssuePhysicalAccessQrUseCase {

    /**
     * Issues a short-lived QR credential for a session the student is assigned to.
     *
     * <p>Fails when the session does not exist or is cancelled, when it is outside its check-in
     * window, or when the student holds no confirmed assignment for it.
     *
     * @param sessionId the target session's opaque id.
     * @param studentId the acting, authenticated student's id — never taken
     *     from the request body, always the JWT principal.
     * @return a freshly-issued, short-lived QR credential.
     */
    AccessQrView issue(String sessionId, UUID studentId);
}
