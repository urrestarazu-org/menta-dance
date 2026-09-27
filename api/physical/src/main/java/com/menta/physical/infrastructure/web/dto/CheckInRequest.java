package com.menta.physical.infrastructure.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code POST /api/v1/physical/sessions/{sessionId}/check-ins}, a
 * true discriminated union between the door reader's QR flow
 * (US-PHYSICAL-001 escenario 2) and the receptionist's MANUAL flow (#45,
 * US-PHYSICAL-008).
 *
 * <p>Presence is variant-conditional, so per-field {@code @NotBlank} cannot
 * express it: {@code isWellFormedQrVariant()} and {@code
 * isWellFormedManualVariant()} are the only presence authority. Each
 * predicate is vacuously {@code true} for the other variant, so a single
 * mixed body never collects two contradictory violations, and an unknown
 * {@code type} is rejected by {@code @Pattern} alone. Any violation raises
 * {@code MethodArgumentNotValidException}, mapped by {@code
 * PhysicalCheckInExceptionHandler} to {@code 400 INVALID_REQUEST} before
 * either use case runs.</p>
 */
public record CheckInRequest(
    @NotBlank @Pattern(regexp = "QR|MANUAL") String type,
    String qrCredentials,
    String deviceId,
    String deviceToken,
    String studentId
) {

    /** #45: a QR body must carry the reader triple and must NOT carry studentId. */
    @AssertTrue(message = "Invalid QR check-in body")
    public boolean isWellFormedQrVariant() {
        if (!"QR".equals(type)) {
            return true;
        }
        return present(qrCredentials) && present(deviceId) && present(deviceToken)
            && studentId == null;
    }

    /** #45: a MANUAL body must carry studentId and must NOT carry any QR-only field. */
    @AssertTrue(message = "Invalid MANUAL check-in body")
    public boolean isWellFormedManualVariant() {
        if (!"MANUAL".equals(type)) {
            return true;
        }
        return present(studentId)
            && qrCredentials == null && deviceId == null && deviceToken == null;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
