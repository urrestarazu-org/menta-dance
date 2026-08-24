package com.menta.physical.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Door-reader payload for {@code POST
 * /api/v1/physical/sessions/{sessionId}/check-ins} (US-PHYSICAL-001
 * escenario 2). {@code type} is fixed to the literal {@code "QR"}: the
 * pattern constraint rejects anything else (e.g. a future {@code "MANUAL"}
 * variant, issue #45) with a plain {@code 400 INVALID_REQUEST} before it
 * ever reaches the use case — no enum needed on the wire for a single valid
 * value today.
 */
public record CheckInRequest(
    @NotBlank @Pattern(regexp = "QR") String type,
    @NotBlank String qrCredentials,
    @NotBlank String deviceId,
    @NotBlank String deviceToken
) {
}
