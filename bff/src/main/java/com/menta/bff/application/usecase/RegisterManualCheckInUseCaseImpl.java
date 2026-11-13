package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CheckInReceipt;
import com.menta.bff.application.port.out.PhysicalReceptionApiClient;
import com.menta.bff.application.usecase.ManualCheckInResult.Outcome;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link RegisterManualCheckInUseCase}. A failure is logged by its class and reason only:
 * neither the token nor the student or session identifiers may reach a log.
 */
@Slf4j
@RequiredArgsConstructor
public class RegisterManualCheckInUseCaseImpl implements RegisterManualCheckInUseCase {

    private final PhysicalReceptionApiClient receptionApiClient;
    private final ZoneId displayZone;

    @Override
    public ManualCheckInResult execute(String accessToken, String sessionId, String studentId) {
        String session = UuidFormat.normalize(sessionId);
        String student = UuidFormat.normalize(studentId);
        if (session == null || student == null) {
            return ManualCheckInResult.of(Outcome.INVALID);
        }
        if (accessToken == null || accessToken.isBlank()) {
            return ManualCheckInResult.of(Outcome.UNAVAILABLE);
        }
        try {
            CheckInReceipt receipt = receptionApiClient.registerManualCheckIn(accessToken, session, student);
            return new ManualCheckInResult(
                    receipt.alreadyRegistered() ? Outcome.ALREADY_REGISTERED : Outcome.REGISTERED,
                    ClassLabels.moment(receipt.recordedAt(), displayZone),
                    "QR".equals(receipt.kind()) ? "QR" : "manual");
        } catch (PhysicalReceptionApiClient.CheckInRejectedException e) {
            return ManualCheckInResult.of(switch (e.getReason()) {
                case CAPACITY_ASSIGNMENT_REQUIRED -> Outcome.NO_SPOT;
                case OUTSIDE_CHECK_IN_WINDOW -> Outcome.OUTSIDE_WINDOW;
                case SESSION_NOT_ACTIVE -> Outcome.SESSION_CANCELLED;
            });
        } catch (PhysicalReceptionApiClient.ServiceUnavailableException e) {
            log.warn("Manual check-in unavailable: {}", e.getClass().getSimpleName());
            return ManualCheckInResult.of(Outcome.UNAVAILABLE);
        }
    }
}
