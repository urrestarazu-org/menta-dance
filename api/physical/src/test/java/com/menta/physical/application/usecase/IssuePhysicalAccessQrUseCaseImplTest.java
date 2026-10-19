package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.AccessQrView;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.application.port.out.QrCredentialSignatureService;
import com.menta.physical.domain.exception.CapacityAssignmentRequiredException;
import com.menta.physical.domain.exception.OutsideCheckInWindowException;
import com.menta.physical.domain.exception.SessionCancelledException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IssuePhysicalAccessQrUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-08-25T20:00:00Z");
    private static final Duration WINDOW_BEFORE = Duration.ofMinutes(30);
    private static final Duration WINDOW_AFTER = Duration.ofHours(2);

    private final PhysicalSessionRepository sessionRepository =
        mock(PhysicalSessionRepository.class);
    private final PhysicalCapacityAssignmentRepository assignmentRepository =
        mock(PhysicalCapacityAssignmentRepository.class);
    private final QrCredentialSignatureService signatureService =
        mock(QrCredentialSignatureService.class);
    private final Clock clock = mock(Clock.class);
    private IssuePhysicalAccessQrUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        when(clock.now()).thenReturn(NOW);
        useCase = new IssuePhysicalAccessQrUseCaseImpl(
            sessionRepository, assignmentRepository, signatureService, clock,
            Duration.ofSeconds(60), WINDOW_BEFORE, WINDOW_AFTER
        );
    }

    /** Scheduled exactly at {@code NOW} — comfortably inside the default window. */
    private static PhysicalSession scheduledSession() {
        return scheduledSession(NOW);
    }

    private static PhysicalSession scheduledSession(Instant scheduledAt) {
        return PhysicalSession.create(CourseId.generate(), scheduledAt, 20, null);
    }

    @Test
    void throws_when_the_session_does_not_exist() {
        SessionId id = SessionId.generate();
        UUID studentId = UUID.randomUUID();
        when(sessionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.issue(id.toString(), studentId))
            .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void throws_when_the_session_has_been_cancelled() {
        SessionId id = SessionId.generate();
        UUID studentId = UUID.randomUUID();
        PhysicalSession cancelled = scheduledSession().cancel();
        when(sessionRepository.findById(id)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> useCase.issue(id.toString(), studentId))
            .isInstanceOf(SessionCancelledException.class);
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void throws_when_the_session_is_outside_the_check_in_window() {
        SessionId id = SessionId.generate();
        UUID studentId = UUID.randomUUID();
        // 10 days out is comfortably outside [-30m, +2h] on either side.
        PhysicalSession farAway = scheduledSession(NOW.plusSeconds(10 * 24 * 3600));
        when(sessionRepository.findById(id)).thenReturn(Optional.of(farAway));

        assertThatThrownBy(() -> useCase.issue(id.toString(), studentId))
            .isInstanceOf(OutsideCheckInWindowException.class);
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void throws_when_the_student_has_no_confirmed_assignment() {
        SessionId id = SessionId.generate();
        UUID studentId = UUID.randomUUID();
        when(sessionRepository.findById(id)).thenReturn(Optional.of(scheduledSession()));
        when(assignmentRepository.existsConfirmedAssignment(id, studentId)).thenReturn(false);

        assertThatThrownBy(() -> useCase.issue(id.toString(), studentId))
            .isInstanceOf(CapacityAssignmentRequiredException.class);
    }

    @Test
    void never_checks_the_assignment_before_the_session_is_confirmed_to_exist() {
        SessionId id = SessionId.generate();
        UUID studentId = UUID.randomUUID();
        when(sessionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.issue(id.toString(), studentId))
            .isInstanceOf(SessionNotFoundException.class);
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void issues_a_credential_expiring_after_the_configured_ttl_with_a_thirty_second_refresh_hint() {
        SessionId id = SessionId.generate();
        UUID studentId = UUID.randomUUID();
        when(sessionRepository.findById(id)).thenReturn(Optional.of(scheduledSession()));
        when(assignmentRepository.existsConfirmedAssignment(id, studentId)).thenReturn(true);
        when(signatureService.sign(
            eq(studentId.toString()), eq(id.toString()), anyString(),
            eq(NOW.plusSeconds(60).getEpochSecond())
        )).thenReturn("qr:token");

        AccessQrView view = useCase.issue(id.toString(), studentId);

        assertThat(view.qrCredentials()).isEqualTo("qr:token");
        assertThat(view.expiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(view.refreshAfterSeconds()).isEqualTo(30);
    }

    @Test
    void issues_a_fresh_jti_on_every_call() {
        SessionId id = SessionId.generate();
        UUID studentId = UUID.randomUUID();
        when(sessionRepository.findById(id)).thenReturn(Optional.of(scheduledSession()));
        when(assignmentRepository.existsConfirmedAssignment(id, studentId)).thenReturn(true);
        when(signatureService.sign(anyString(), anyString(), anyString(), anyLong()))
            .thenAnswer(invocation -> invocation.getArgument(2));

        String firstJti = useCase.issue(id.toString(), studentId).qrCredentials();
        String secondJti = useCase.issue(id.toString(), studentId).qrCredentials();

        assertThat(firstJti).isNotEqualTo(secondJti);
    }
}
