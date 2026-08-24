package com.menta.physical.infrastructure.web.dto;

import com.menta.physical.application.dto.AccessQrView;

/**
 * Web-layer projection of {@link AccessQrView} for {@code POST
 * /api/v1/physical/sessions/{sessionId}/access-qr} (US-PHYSICAL-001
 * escenario 1). Not a persisted resource — the endpoint returns
 * {@code 200 OK}, not {@code 201 Created}.
 */
public record AccessQrResponse(
    String qrCredentials,
    String expiresAt,
    int refreshAfterSeconds
) {

    public static AccessQrResponse from(AccessQrView view) {
        return new AccessQrResponse(
            view.qrCredentials(), view.expiresAt().toString(), view.refreshAfterSeconds()
        );
    }
}
