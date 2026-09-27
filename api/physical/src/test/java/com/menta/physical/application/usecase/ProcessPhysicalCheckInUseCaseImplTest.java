package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.CheckInActor;
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
import com.menta.physical.domain.exception.InsufficientRoleException;
import com.menta.physical.domain.exception.InvalidDeviceTokenException;
import com.menta.physical.domain.exception.InvalidQrCredentialException;
import com.menta.physical.domain.exception.OutsideCheckInWindowException;
import com.menta.physical.domain.exception.SessionCancelledException;
import com.menta.physical.domain.exception.SessionNotActiveException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.domain.exception.StudentNotFoundException;
import com.menta.physical.domain.model.Attendance;
import com.menta.physical.domain.model.AttendanceKind;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.domain.model.SessionStatus;
import com.menta.shared.auth.UserExistencePort;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

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
    private final UserExistencePort userExistencePort = mock(UserExistencePort.class);

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
            DEVICE_TOKEN, WINDOW_BEFORE, WINDOW_AFTER, LOCK_TTL, userExistencePort
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
        return CheckInCommand.qr(sessionId, qrCredentials, DEVICE_ID, DEVICE_TOKEN);
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
            CheckInCommand.qr(sessionId, "irrelevant", DEVICE_ID, "wrong-secret");

        assertThatThrownBy(() -> useCase.checkIn(command))
            .isInstanceOf(InvalidDeviceTokenException.class);
        verifyNoInteractions(
            sessionRepository, assignmentRepository, attendanceRepository, lockPort
        );
    }

    @Test
    void rejects_a_null_device_token() {
        CheckInCommand command = CheckInCommand.qr(sessionId, "irrelevant", DEVICE_ID, null);

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

    /**
     * #45, US-PHYSICAL-008: the receptionist-initiated MANUAL variant. D7's asserted order is
     * role -> student existence -> confirmed assignment -> not-cancelled -> idempotent read ->
     * single lock -> INSERT. New tests only; the QR tests above are untouched.
     */
    @Nested
    class CheckInManuallyTests {

        private static final String ATTENDANCE_LOCK_PREFIX = "checkin:attendance:";

        private CheckInActor receptionist(UUID actorId) {
            return new CheckInActor(actorId, Set.of("RECEPTIONIST"));
        }

        private CheckInActor admin(UUID actorId) {
            return new CheckInActor(actorId, Set.of("ADMIN"));
        }

        private PhysicalSession session(SessionStatus status, Instant scheduledAt) {
            return new PhysicalSession(
                sessionId, CourseId.generate(), scheduledAt, 20, 0, 0, status, null
            );
        }

        private void allowHappyPathUpToLocks() {
            when(userExistencePort.existsById(studentId)).thenReturn(true);
            when(assignmentRepository.existsConfirmedAssignment(sessionId, studentId))
                .thenReturn(true);
            when(sessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session(SessionStatus.SCHEDULED, NOW)));
            when(attendanceRepository.findBySessionIdAndUserId(sessionId, studentId))
                .thenReturn(Optional.empty());
        }

        @Test
        void receptionist_reaches_past_the_role_gate() {
            UUID actorId = UUID.randomUUID();
            allowHappyPathUpToLocks();
            when(lockPort.acquireIfAbsent(anyString(), eq(LOCK_TTL))).thenReturn(Optional.of("nonce"));
            when(attendanceRepository.save(any(Attendance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            CheckInResult result =
                useCase.checkInManually(CheckInCommand.manual(sessionId, studentId, receptionist(actorId)));

            assertThat(result.newlyRecorded()).isTrue();
        }

        @Test
        void admin_also_reaches_past_the_role_gate() {
            UUID actorId = UUID.randomUUID();
            allowHappyPathUpToLocks();
            when(lockPort.acquireIfAbsent(anyString(), eq(LOCK_TTL))).thenReturn(Optional.of("nonce"));
            when(attendanceRepository.save(any(Attendance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            CheckInResult result =
                useCase.checkInManually(CheckInCommand.manual(sessionId, studentId, admin(actorId)));

            assertThat(result.newlyRecorded()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"STUDENT", "INSTRUCTOR"})
        void rejects_a_caller_without_the_required_role(String roleName) {
            CheckInActor actor = new CheckInActor(UUID.randomUUID(), Set.of(roleName));

            assertThatThrownBy(() ->
                useCase.checkInManually(CheckInCommand.manual(sessionId, studentId, actor)))
                .isInstanceOf(InsufficientRoleException.class);
            verifyNoInteractions(userExistencePort, assignmentRepository, sessionRepository, lockPort);
        }

        @Test
        void rejects_an_anonymous_caller() {
            assertThatThrownBy(() -> useCase.checkInManually(
                CheckInCommand.manual(sessionId, studentId, CheckInActor.anonymous())))
                .isInstanceOf(InsufficientRoleException.class);
            verifyNoInteractions(userExistencePort, assignmentRepository, sessionRepository, lockPort);
        }

        @Test
        void rejects_an_unknown_student_before_touching_physical_state() {
            when(userExistencePort.existsById(studentId)).thenReturn(false);

            assertThatThrownBy(() -> useCase.checkInManually(
                CheckInCommand.manual(sessionId, studentId, receptionist(UUID.randomUUID()))))
                .isInstanceOf(StudentNotFoundException.class);
            verifyNoInteractions(assignmentRepository, sessionRepository, lockPort);
        }

        @Test
        void rejects_when_no_confirmed_assignment_exists() {
            when(userExistencePort.existsById(studentId)).thenReturn(true);
            when(assignmentRepository.existsConfirmedAssignment(sessionId, studentId))
                .thenReturn(false);

            assertThatThrownBy(() -> useCase.checkInManually(
                CheckInCommand.manual(sessionId, studentId, receptionist(UUID.randomUUID()))))
                .isInstanceOf(CapacityAssignmentRequiredException.class);
            verifyNoInteractions(sessionRepository, lockPort);
        }

        @Test
        void rejects_a_cancelled_session() {
            when(userExistencePort.existsById(studentId)).thenReturn(true);
            when(assignmentRepository.existsConfirmedAssignment(sessionId, studentId))
                .thenReturn(true);
            when(sessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session(SessionStatus.CANCELLED, NOW)));

            assertThatThrownBy(() -> useCase.checkInManually(
                CheckInCommand.manual(sessionId, studentId, receptionist(UUID.randomUUID()))))
                .isInstanceOf(SessionNotActiveException.class);
            verifyNoInteractions(attendanceRepository, lockPort);
        }

        @Test
        void accepts_an_elapsed_non_cancelled_session_with_no_time_limit() {
            // D8: hasOccurred is never consulted for MANUAL. A session scheduled far in the
            // past, still SCHEDULED (not CANCELLED), must be accepted with no time limit.
            when(userExistencePort.existsById(studentId)).thenReturn(true);
            when(assignmentRepository.existsConfirmedAssignment(sessionId, studentId))
                .thenReturn(true);
            when(sessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session(SessionStatus.SCHEDULED, NOW.minusSeconds(365L * 24 * 3600))));
            when(attendanceRepository.findBySessionIdAndUserId(sessionId, studentId))
                .thenReturn(Optional.empty());
            when(lockPort.acquireIfAbsent(anyString(), eq(LOCK_TTL))).thenReturn(Optional.of("nonce"));
            when(attendanceRepository.save(any(Attendance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            CheckInResult result = useCase.checkInManually(
                CheckInCommand.manual(sessionId, studentId, receptionist(UUID.randomUUID())));

            assertThat(result.newlyRecorded()).isTrue();
        }

        @Test
        void unknown_student_on_a_cancelled_session_yields_student_not_found_not_session_not_active() {
            when(userExistencePort.existsById(studentId)).thenReturn(false);
            when(sessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session(SessionStatus.CANCELLED, NOW)));

            assertThatThrownBy(() -> useCase.checkInManually(
                CheckInCommand.manual(sessionId, studentId, receptionist(UUID.randomUUID()))))
                .isInstanceOf(StudentNotFoundException.class);
            verifyNoInteractions(assignmentRepository, sessionRepository, lockPort);
        }

        @Test
        void missing_assignment_on_a_cancelled_session_yields_capacity_required_not_session_not_active() {
            when(userExistencePort.existsById(studentId)).thenReturn(true);
            when(assignmentRepository.existsConfirmedAssignment(sessionId, studentId))
                .thenReturn(false);
            when(sessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session(SessionStatus.CANCELLED, NOW)));

            assertThatThrownBy(() -> useCase.checkInManually(
                CheckInCommand.manual(sessionId, studentId, receptionist(UUID.randomUUID()))))
                .isInstanceOf(CapacityAssignmentRequiredException.class);
            verifyNoInteractions(sessionRepository, lockPort);
        }

        @Test
        void an_unknown_session_yields_capacity_required_because_no_orphan_assignment_can_exist() {
            when(userExistencePort.existsById(studentId)).thenReturn(true);
            when(assignmentRepository.existsConfirmedAssignment(sessionId, studentId))
                .thenReturn(false);
            when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.checkInManually(
                CheckInCommand.manual(sessionId, studentId, receptionist(UUID.randomUUID()))))
                .isInstanceOf(CapacityAssignmentRequiredException.class);
            verifyNoInteractions(sessionRepository, lockPort);
        }

        @Test
        void an_unreachable_unknown_session_with_a_mocked_confirmed_assignment_yields_session_not_found() {
            // Defense-in-depth only: an orphan confirmed assignment for an unknown session
            // cannot occur in production (FK constraint) but is reachable via mocked ports.
            when(userExistencePort.existsById(studentId)).thenReturn(true);
            when(assignmentRepository.existsConfirmedAssignment(sessionId, studentId))
                .thenReturn(true);
            when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.checkInManually(
                CheckInCommand.manual(sessionId, studentId, receptionist(UUID.randomUUID()))))
                .isInstanceOf(SessionNotFoundException.class);
            verifyNoInteractions(lockPort);
        }

        @Test
        void an_existing_attendance_replays_idempotently_without_touching_redis() {
            when(userExistencePort.existsById(studentId)).thenReturn(true);
            when(assignmentRepository.existsConfirmedAssignment(sessionId, studentId))
                .thenReturn(true);
            when(sessionRepository.findById(sessionId))
                .thenReturn(Optional.of(session(SessionStatus.SCHEDULED, NOW)));
            Attendance existing =
                Attendance.record(sessionId, studentId, NOW, UUID.randomUUID().toString(), AttendanceKind.MANUAL);
            when(attendanceRepository.findBySessionIdAndUserId(sessionId, studentId))
                .thenReturn(Optional.of(existing));

            CheckInResult result = useCase.checkInManually(
                CheckInCommand.manual(sessionId, studentId, receptionist(UUID.randomUUID())));

            assertThat(result.newlyRecorded()).isFalse();
            assertThat(result.attendance().userId()).isEqualTo(studentId);
            verifyNoInteractions(lockPort);
            verify(attendanceRepository, never()).save(any());
        }

        @Test
        void acquires_exactly_one_lock_with_the_shared_attendance_key_and_persists_the_actor_as_device_id() {
            UUID actorId = UUID.randomUUID();
            allowHappyPathUpToLocks();
            when(lockPort.acquireIfAbsent(anyString(), eq(LOCK_TTL))).thenReturn(Optional.of("nonce"));
            ArgumentCaptor<Attendance> attendanceCaptor = ArgumentCaptor.forClass(Attendance.class);
            when(attendanceRepository.save(attendanceCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));

            CheckInResult result = useCase.checkInManually(
                CheckInCommand.manual(sessionId, studentId, receptionist(actorId)));

            assertThat(result.newlyRecorded()).isTrue();
            String expectedKey = ATTENDANCE_LOCK_PREFIX + sessionId + ":" + studentId;
            verify(lockPort, times(1)).acquireIfAbsent(eq(expectedKey), eq(LOCK_TTL));
            verify(lockPort, never()).acquireIfAbsent(startsWith("checkin:qr:"), any());
            Attendance persisted = attendanceCaptor.getValue();
            assertThat(persisted.getKind()).isEqualTo(AttendanceKind.MANUAL);
            assertThat(persisted.getDeviceId()).isEqualTo(actorId.toString());
            assertThat(persisted.getUserId()).isEqualTo(studentId);
            assertThat(persisted.getSessionId()).isEqualTo(sessionId);
        }
    }
}
