package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.CheckInCommand;
import com.menta.physical.application.dto.CheckInResult;
import com.menta.physical.application.port.out.AttendanceRepository;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.application.port.out.QrCredentialSignatureService;
import com.menta.physical.application.port.out.RedisLockPort;
import com.menta.physical.domain.exception.CapacityAssignmentRequiredException;
import com.menta.physical.domain.exception.CheckInAlreadyProcessingException;
import com.menta.physical.domain.exception.CheckInDegradedException;
import com.menta.physical.domain.exception.ExpiredQrCredentialException;
import com.menta.physical.domain.exception.InvalidDeviceTokenException;
import com.menta.physical.domain.exception.InvalidQrCredentialException;
import com.menta.physical.domain.exception.OutsideCheckInWindowException;
import com.menta.physical.domain.exception.SessionCancelledException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.domain.model.Attendance;
import com.menta.physical.domain.model.AttendanceKind;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.domain.model.SessionStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessPhysicalCheckInUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-08-25T20:00:00Z");
    private static final String DEVICE_TOKEN = "shared-secret";
    private static final Duration WINDOW_BEFORE = Duration.ofMinutes(30);
    private static final Duration WINDOW_AFTER = Duration.ofHours(2);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final String DEVICE_ID = "reader-01";
    private static final String JTI = "jti-1";

    private final PhysicalSessionRepository sessionRepository =
        mock(PhysicalSessionRepository.class);
    private final PhysicalCapacityAssignmentRepository assignmentRepository =
        mock(PhysicalCapacityAssignmentRepository.class);
    private final AttendanceRepository attendanceRepository =
        mock(AttendanceRepository.class);
    private final QrCredentialSignatureService signatureService =
        mock(QrCredentialSignatureService.class);
    private final RedisLockPort lockPort = mock(RedisLockPort.class);
    private final Clock clock = mock(Clock.class);

    private SessionId sessionId;
    private UUID studentId;
    private ProcessPhysicalCheckInUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        when(clock.now()).thenReturn(NOW);
        sessionId = SessionId.generate();
        studentId = UUID.randomUUID();
        useCase = new ProcessPhysicalCheckInUseCaseImpl(
            sessionRepository, assignmentRepository, attendanceRepository,
            signatureService, lockPort, clock,
            DEVICE_TOKEN, WINDOW_BEFORE, WINDOW_AFTER, LOCK_TTL
        );
    }

    private static String rawToken(UUID student, SessionId session, String jti, long expiresAt) {
        return "qr:" + student + ":" + session + ":" + jti + ":" + expiresAt;
    }

    private String validToken() {
        return rawToken(studentId, sessionId, JTI, NOW.plusSeconds(60).getEpochSecond());
    }

    private void stubMatchingSignature(String token) {
        when(signatureService.sign(any(), any(), any(), anyLong())).thenReturn(token);
    }

    private PhysicalSession scheduledSession(Instant scheduledAt) {
        return new PhysicalSession(
            sessionId, CourseId.generate(), scheduledAt, 20, 0, 0, SessionStatus.SCHEDULED, null
        );
    }

    private CheckInCommand command(String qrCredentials) {
        return new CheckInCommand(sessionId, null, qrCredentials, DEVICE_ID, DEVICE_TOKEN);
    }

    private void allowHappyPathUpToLocks() {
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.of(scheduledSession(NOW)));
        when(assignmentRepository.existsConfirmedAssignment(sessionId, studentId))
            .thenReturn(true);
        when(attendanceRepository.findBySessionIdAndUserId(sessionId, studentId))
            .thenReturn(Optional.empty());
    }

    @Test
    void records_a_new_attendance_and_reports_it_as_newly_recorded() {
        String token = validToken();
        stubMatchingSignature(token);
        allowHappyPathUpToLocks();
        when(lockPort.acquireIfAbsent(anyString(), eq(LOCK_TTL))).thenReturn(Optional.of("nonce"));
        when(attendanceRepository.save(any(Attendance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CheckInResult result = useCase.checkIn(command(token));

        assertThat(result.newlyRecorded()).isTrue();
        assertThat(result.attendance().userId()).isEqualTo(studentId);
        assertThat(result.attendance().sessionId()).isEqualTo(sessionId);
        assertThat(result.attendance().deviceId()).isEqualTo(DEVICE_ID);
        verify(lockPort).acquireIfAbsent(eq("checkin:qr:" + JTI), eq(LOCK_TTL));
        String attendanceKey = "checkin:attendance:" + sessionId + ":" + studentId;
        verify(lockPort).acquireIfAbsent(eq(attendanceKey), eq(LOCK_TTL));
    }

    @Test
    void a_second_scan_of_the_same_qr_replays_idempotently_without_touching_redis() {
        String token = validToken();
        stubMatchingSignature(token);
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.of(scheduledSession(NOW)));
        when(assignmentRepository.existsConfirmedAssignment(sessionId, studentId))
            .thenReturn(true);
        Attendance existing =
            Attendance.record(sessionId, studentId, NOW, DEVICE_ID, AttendanceKind.QR);
        when(attendanceRepository.findBySessionIdAndUserId(sessionId, studentId))
            .thenReturn(Optional.of(existing));

        CheckInResult result = useCase.checkIn(command(token));

        assertThat(result.newlyRecorded()).isFalse();
        assertThat(result.attendance().userId()).isEqualTo(studentId);
        verifyNoInteractions(lockPort);
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void rejects_before_any_other_check_when_the_device_token_is_wrong() {
        CheckInCommand command =
            new CheckInCommand(sessionId, null, "irrelevant", DEVICE_ID, "wrong-secret");

        assertThatThrownBy(() -> useCase.checkIn(command))
            .isInstanceOf(InvalidDeviceTokenException.class);
        verifyNoInteractions(
            sessionRepository, assignmentRepository, attendanceRepository, lockPort
        );
    }

    @Test
    void rejects_a_null_device_token() {
        CheckInCommand command = new CheckInCommand(sessionId, null, "irrelevant", DEVICE_ID, null);

        assertThatThrownBy(() -> useCase.checkIn(command))
            .isInstanceOf(InvalidDeviceTokenException.class);
    }

    @Test
    void rejects_a_malformed_qr_credential_before_touching_redis() {
        CheckInCommand command = command("not-a-token");

        assertThatThrownBy(() -> useCase.checkIn(command))
            .isInstanceOf(InvalidQrCredentialException.class);
        verifyNoInteractions(lockPort);
    }

    @Test
    void rejects_a_qr_credential_bound_for_a_different_session() {
        SessionId otherSession = SessionId.generate();
        String token = rawToken(studentId, otherSession, JTI, NOW.plusSeconds(60).getEpochSecond());

        assertThatThrownBy(() -> useCase.checkIn(command(token)))
            .isInstanceOf(InvalidQrCredentialException.class);
        verifyNoInteractions(lockPort);
    }

    @Test
    void rejects_a_qr_credential_whose_recomputed_signature_does_not_match() {
        String token = validToken();
        when(signatureService.sign(any(), any(), any(), anyLong()))
            .thenReturn("qr:tampered:value:x:0");

        assertThatThrownBy(() -> useCase.checkIn(command(token)))
            .isInstanceOf(InvalidQrCredentialException.class);
        verifyNoInteractions(lockPort);
    }

    @Test
    void rejects_an_expired_qr_credential() {
        String token = rawToken(studentId, sessionId, JTI, NOW.minusSeconds(1).getEpochSecond());
        stubMatchingSignature(token);

        assertThatThrownBy(() -> useCase.checkIn(command(token)))
            .isInstanceOf(ExpiredQrCredentialException.class);
        verifyNoInteractions(lockPort);
    }

    @Test
    void rejects_when_the_target_session_does_not_exist() {
        String token = validToken();
        stubMatchingSignature(token);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.checkIn(command(token)))
            .isInstanceOf(SessionNotFoundException.class);
        verifyNoInteractions(lockPort);
    }

    @Test
    void rejects_a_check_in_for_a_cancelled_session_before_touching_redis() {
        String token = validToken();
        stubMatchingSignature(token);
        PhysicalSession cancelled = scheduledSession(NOW).cancel();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> useCase.checkIn(command(token)))
            .isInstanceOf(SessionCancelledException.class);
        verifyNoInteractions(assignmentRepository, attendanceRepository, lockPort);
    }

    @Test
    void rejects_a_check_in_outside_the_session_window() {
        String token = validToken();
        stubMatchingSignature(token);
        // 10h away is outside [scheduledAt-30m, scheduledAt+2h] no matter which side.
        PhysicalSession farAwaySession = scheduledSession(NOW.plusSeconds(36000));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(farAwaySession));

        assertThatThrownBy(() -> useCase.checkIn(command(token)))
            .isInstanceOf(OutsideCheckInWindowException.class);
        verifyNoInteractions(lockPort);
    }

    @Test
    void rejects_when_no_confirmed_assignment_exists() {
        String token = validToken();
        stubMatchingSignature(token);
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.of(scheduledSession(NOW)));
        when(assignmentRepository.existsConfirmedAssignment(sessionId, studentId))
            .thenReturn(false);

        assertThatThrownBy(() -> useCase.checkIn(command(token)))
            .isInstanceOf(CapacityAssignmentRequiredException.class);
        verifyNoInteractions(lockPort);
    }

    @Test
    void rejects_with_already_processing_when_the_qr_lock_is_already_held() {
        String token = validToken();
        stubMatchingSignature(token);
        allowHappyPathUpToLocks();
        when(lockPort.acquireIfAbsent(eq("checkin:qr:" + JTI), eq(LOCK_TTL)))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.checkIn(command(token)))
            .isInstanceOf(CheckInAlreadyProcessingException.class);
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void rejects_with_already_processing_when_the_attendance_lock_is_already_held() {
        String token = validToken();
        stubMatchingSignature(token);
        allowHappyPathUpToLocks();
        when(lockPort.acquireIfAbsent(eq("checkin:qr:" + JTI), eq(LOCK_TTL)))
            .thenReturn(Optional.of("nonce"));
        String attendanceKey = "checkin:attendance:" + sessionId + ":" + studentId;
        when(lockPort.acquireIfAbsent(eq(attendanceKey), eq(LOCK_TTL)))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.checkIn(command(token)))
            .isInstanceOf(CheckInAlreadyProcessingException.class);

        // The first lock is left alive on purpose -- no compensating release call
        // exists on the port at all (see RedisLockPort's Javadoc).
        verify(lockPort, times(1)).acquireIfAbsent(eq("checkin:qr:" + JTI), eq(LOCK_TTL));
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void propagates_a_degraded_failure_when_redis_is_unavailable() {
        String token = validToken();
        stubMatchingSignature(token);
        allowHappyPathUpToLocks();
        when(lockPort.acquireIfAbsent(anyString(), eq(LOCK_TTL)))
            .thenThrow(new CheckInDegradedException());

        assertThatThrownBy(() -> useCase.checkIn(command(token)))
            .isInstanceOf(CheckInDegradedException.class);
        verify(attendanceRepository, never()).save(any());
    }
}
