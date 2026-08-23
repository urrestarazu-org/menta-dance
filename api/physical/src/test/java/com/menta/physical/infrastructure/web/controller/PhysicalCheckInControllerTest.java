package com.menta.physical.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.AccessQrView;
import com.menta.physical.application.dto.AttendanceView;
import com.menta.physical.application.dto.CheckInCommand;
import com.menta.physical.application.dto.CheckInResult;
import com.menta.physical.application.port.in.IssuePhysicalAccessQrUseCase;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.physical.domain.model.AttendanceId;
import com.menta.physical.domain.model.AttendanceKind;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.infrastructure.web.dto.AccessQrResponse;
import com.menta.physical.infrastructure.web.dto.CheckInRequest;
import com.menta.physical.infrastructure.web.dto.CheckInResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class PhysicalCheckInControllerTest {

    private IssuePhysicalAccessQrUseCase issueUseCase;
    private ProcessPhysicalCheckInUseCase checkInUseCase;
    private PhysicalCheckInController controller;

    @BeforeEach
    void setUp() {
        issueUseCase = mock(IssuePhysicalAccessQrUseCase.class);
        checkInUseCase = mock(ProcessPhysicalCheckInUseCase.class);
        controller = new PhysicalCheckInController(issueUseCase, checkInUseCase);
    }

    private static Authentication authOf(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
            userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
    }

    private static AttendanceView attendanceView(SessionId sessionId, UUID studentId) {
        return new AttendanceView(
            AttendanceId.generate(), sessionId, studentId, Instant.parse("2026-09-15T22:00:00Z"),
            "reader-1", AttendanceKind.QR
        );
    }

    @Test
    void issue_access_qr_resolves_the_student_from_the_authentication_and_returns_200() {
        UUID studentId = UUID.randomUUID();
        String sessionId = UUID.randomUUID().toString();
        AccessQrView view = new AccessQrView("qr:token", Instant.parse("2026-09-15T21:59:00Z"), 30);
        when(issueUseCase.issue(sessionId, studentId)).thenReturn(view);

        ResponseEntity<AccessQrResponse> response =
            controller.issueAccessQr(sessionId, authOf(studentId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().qrCredentials()).isEqualTo("qr:token");
        verify(issueUseCase).issue(sessionId, studentId);
    }

    @Test
    void check_in_never_reads_authentication_and_builds_a_command_with_a_null_student_id_from_qr() {
        SessionId sessionId = SessionId.generate();
        CheckInRequest request =
            new CheckInRequest("QR", "qr:payload", "reader-1", "device-secret");
        AttendanceView view = attendanceView(sessionId, UUID.randomUUID());
        when(checkInUseCase.checkIn(any())).thenReturn(new CheckInResult(view, true));

        ResponseEntity<CheckInResponse> response =
            controller.checkIn(sessionId.toString(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().attendanceId()).isEqualTo(view.attendanceId().toString());
        verify(checkInUseCase).checkIn(eq(new CheckInCommand(
            sessionId, null, "qr:payload", "reader-1", "device-secret"
        )));
    }

    @Test
    void check_in_returns_200_when_the_use_case_reports_an_idempotent_replay() {
        SessionId sessionId = SessionId.generate();
        CheckInRequest request =
            new CheckInRequest("QR", "qr:payload", "reader-1", "device-secret");
        AttendanceView view = attendanceView(sessionId, UUID.randomUUID());
        when(checkInUseCase.checkIn(any())).thenReturn(new CheckInResult(view, false));

        ResponseEntity<CheckInResponse> response =
            controller.checkIn(sessionId.toString(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
