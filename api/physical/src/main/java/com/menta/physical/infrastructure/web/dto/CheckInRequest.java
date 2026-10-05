package com.menta.physical.infrastructure.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
 *
 * <p>ADR-0044: the QR-only fields are length-capped so the device token hash and the
 * rate-limit fingerprint are only ever computed over bounded input. {@code null} stays valid
 * for {@code @Size}; presence is still decided by the predicates above.</p>
 */
public record CheckInRequest(
    @NotBlank @Pattern(regexp = "QR|MANUAL") String type,
    @Size(max = QR_CREDENTIALS_MAX_LENGTH) String qrCredentials,
    @Size(max = DEVICE_ID_MAX_LENGTH) String deviceId,
    @Size(max = DEVICE_TOKEN_MAX_LENGTH) String deviceToken,
    String studentId
) {

    /** Length of a canonical UUID, the format of {@code deviceId} (ADR-0044). */
    public static final int DEVICE_ID_MAX_LENGTH = 36;

    /**
     * 32 random bytes in unpadded Base64 URL. Coupled to {@code
     * SecureRandomDeviceSecretGenerator}; a test keeps both in sync (ADR-0044).
     */
    public static final int DEVICE_TOKEN_MAX_LENGTH = 43;

    /** Deliberately generous: the current QR format is a placeholder for a real HMAC (ADR-0044). */
    public static final int QR_CREDENTIALS_MAX_LENGTH = 512;

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
