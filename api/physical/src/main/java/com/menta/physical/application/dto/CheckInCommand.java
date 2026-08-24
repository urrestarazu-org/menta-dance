package com.menta.physical.application.dto;

import com.menta.physical.domain.model.SessionId;
import java.util.UUID;

/**
 * Application-layer command for the door-side
 * {@link com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase}.
 * Built by the controller from the request body and a path-variable
 * {@code sessionId}; never touches the persistence layer (US-PHYSICAL-001
 * escenario 2).
 *
 * <p>All fields are required — nulls trip {@code NullPointerException} at the
 * Validation boundary and bubble back as {@code 400} via
 * {@code MethodArgumentNotValidException}. The use case itself assumes a
 * non-null, well-formed command.</p>
 */
public record CheckInCommand(
    SessionId sessionId,
    UUID studentIdFromQr,
    String qrCredentials,
    String deviceId,
    String deviceToken
) {
}
