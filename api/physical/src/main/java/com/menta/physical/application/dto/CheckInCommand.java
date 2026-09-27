package com.menta.physical.application.dto;

import com.menta.physical.domain.model.AttendanceKind;
import com.menta.physical.domain.model.SessionId;
import java.util.UUID;

/**
 * Application-layer command for {@link
 * com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase}.
 * Built by the controller from the request body and a path-variable
 * {@code sessionId}; never touches the persistence layer. {@code type} is
 * the dispatch key between the QR flow (US-PHYSICAL-001 escenario 2) and the
 * MANUAL flow (#45, US-PHYSICAL-008).
 *
 * <p>The two static factories are the only construction path so a command
 * mixing QR and MANUAL fields is unrepresentable at the application
 * boundary: {@link #qr} always carries {@code studentId == null} and {@link
 * CheckInActor#anonymous()}; {@link #manual} always carries the QR-only
 * fields as {@code null}.</p>
 */
public record CheckInCommand(
    SessionId sessionId,
    AttendanceKind type,
    UUID studentId,
    String qrCredentials,
    String deviceId,
    String deviceToken,
    CheckInActor actor
) {

    public static CheckInCommand qr(
        SessionId sessionId, String qrCredentials, String deviceId, String deviceToken
    ) {
        return new CheckInCommand(
            sessionId, AttendanceKind.QR, null, qrCredentials, deviceId, deviceToken,
            CheckInActor.anonymous()
        );
    }

    public static CheckInCommand manual(SessionId sessionId, UUID studentId, CheckInActor actor) {
        return new CheckInCommand(
            sessionId, AttendanceKind.MANUAL, studentId, null, null, null, actor
        );
    }
}
