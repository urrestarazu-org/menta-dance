package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.CheckInReceipt;
import com.menta.bff.application.port.out.PhysicalReceptionApiClient;
import com.menta.bff.application.port.out.PhysicalReceptionApiClient.CheckInRejectedException;
import com.menta.bff.application.usecase.ManualCheckInResult.Outcome;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterManualCheckInUseCase")
class RegisterManualCheckInUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";
    private static final String SESSION_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String STUDENT_ID = "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00";
    private static final Instant RECORDED_AT = Instant.parse("2026-11-13T21:05:00Z");

    @Mock
    private PhysicalReceptionApiClient receptionApiClient;

    private RegisterManualCheckInUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterManualCheckInUseCaseImpl(
                receptionApiClient, ZoneId.of("America/Argentina/Buenos_Aires"));
    }

    @Test
    @DisplayName("a new attendance is registered, worded in the academy's zone")
    void registered() {
        when(receptionApiClient.registerManualCheckIn(TOKEN, SESSION_ID, STUDENT_ID))
                .thenReturn(new CheckInReceipt(false, RECORDED_AT, "MANUAL"));

        ManualCheckInResult result = useCase.execute(TOKEN, SESSION_ID, STUDENT_ID);

        assertThat(result).isEqualTo(new ManualCheckInResult(
                Outcome.REGISTERED, "viernes 13 de noviembre a las 18:05", "manual"));
    }

    @Test
    @DisplayName("the idempotent replay is a result, not an error: already registered, with how and when")
    void alreadyRegistered() {
        when(receptionApiClient.registerManualCheckIn(TOKEN, SESSION_ID, STUDENT_ID))
                .thenReturn(new CheckInReceipt(true, RECORDED_AT, "QR"));

        ManualCheckInResult result = useCase.execute(TOKEN, SESSION_ID, STUDENT_ID);

        assertThat(result).isEqualTo(new ManualCheckInResult(
                Outcome.ALREADY_REGISTERED, "viernes 13 de noviembre a las 18:05", "QR"));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(CheckInRejectedException.Reason.class)
    @DisplayName("each refusal the API words has its own outcome")
    void refusals(CheckInRejectedException.Reason reason) {
        when(receptionApiClient.registerManualCheckIn(TOKEN, SESSION_ID, STUDENT_ID))
                .thenThrow(new CheckInRejectedException(reason));

        Outcome expected = switch (reason) {
            case CAPACITY_ASSIGNMENT_REQUIRED -> Outcome.NO_SPOT;
            case OUTSIDE_CHECK_IN_WINDOW -> Outcome.OUTSIDE_WINDOW;
            case SESSION_NOT_ACTIVE -> Outcome.SESSION_CANCELLED;
        };
        assertThat(useCase.execute(TOKEN, SESSION_ID, STUDENT_ID).outcome()).isEqualTo(expected);
    }

    @Test
    @DisplayName("an unavailable API is the unavailable outcome")
    void unavailable() {
        when(receptionApiClient.registerManualCheckIn(TOKEN, SESSION_ID, STUDENT_ID))
                .thenThrow(new PhysicalReceptionApiClient.ServiceUnavailableException("boom"));

        assertThat(useCase.execute(TOKEN, SESSION_ID, STUDENT_ID).outcome()).isEqualTo(Outcome.UNAVAILABLE);
    }

    @Test
    @DisplayName("the identifiers are normalized before they go to the API")
    void identifiersAreNormalized() {
        when(receptionApiClient.registerManualCheckIn(TOKEN, SESSION_ID, STUDENT_ID))
                .thenReturn(new CheckInReceipt(false, RECORDED_AT, "MANUAL"));

        ManualCheckInResult result =
                useCase.execute(TOKEN, " " + SESSION_ID.toUpperCase() + " ", STUDENT_ID.toUpperCase());

        assertThat(result.outcome()).isEqualTo(Outcome.REGISTERED);
    }

    @ParameterizedTest(name = "[{index}] session id \"{0}\" is invalid")
    @NullAndEmptySource
    @ValueSource(strings = {
        "not-a-uuid", "../x", "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5", "5b6f7c8e1d2a4e3b9c4d0a1b2c3d4e5f"})
    @DisplayName("a session id that is not a UUID is refused without calling the API")
    void malformedSessionId(String sessionId) {
        assertThat(useCase.execute(TOKEN, sessionId, STUDENT_ID).outcome()).isEqualTo(Outcome.INVALID);
        verify(receptionApiClient, never()).registerManualCheckIn(any(), any(), any());
    }

    @ParameterizedTest(name = "[{index}] student id \"{0}\" is invalid")
    @NullAndEmptySource
    @ValueSource(strings = {"not-a-uuid", "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00 ; drop", "0a1b2c3d-1d2a-4e3b-9c4d"})
    @DisplayName("a student id that is not a UUID is refused without calling the API")
    void malformedStudentId(String studentId) {
        assertThat(useCase.execute(TOKEN, SESSION_ID, studentId).outcome()).isEqualTo(Outcome.INVALID);
        verify(receptionApiClient, never()).registerManualCheckIn(any(), any(), any());
    }

    @ParameterizedTest(name = "[{index}] token \"{0}\" is unavailable")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("without an access token the API is not asked")
    void withoutToken(String token) {
        assertThat(useCase.execute(token, SESSION_ID, STUDENT_ID).outcome()).isEqualTo(Outcome.UNAVAILABLE);
        verify(receptionApiClient, never()).registerManualCheckIn(any(), any(), any());
    }
}
