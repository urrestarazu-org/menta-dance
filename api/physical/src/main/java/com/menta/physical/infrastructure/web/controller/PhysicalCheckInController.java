package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.application.dto.AccessQrView;
import com.menta.physical.application.dto.CheckInCommand;
import com.menta.physical.application.dto.CheckInResult;
import com.menta.physical.application.port.in.IssuePhysicalAccessQrUseCase;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.infrastructure.web.dto.AccessQrResponse;
import com.menta.physical.infrastructure.web.dto.CheckInRequest;
import com.menta.physical.infrastructure.web.dto.CheckInResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for the physical check-in flow (US-PHYSICAL-001). Two
 * endpoints with deliberately different authorization models:
 *
 * <ul>
 *   <li>{@code POST /api/v1/physical/sessions/{sessionId}/access-qr} requires
 *   an authenticated student — {@code SecurityConfig} enforces
 *   {@code .authenticated()} with no specific role, and the student's id is
 *   always taken from the JWT principal, never the request body.</li>
 *   <li>{@code POST /api/v1/physical/sessions/{sessionId}/check-ins} is
 *   {@code permitAll()} at the filter level: the door reader authenticates
 *   with a shared {@code deviceToken} the use case itself verifies, so this
 *   endpoint never touches {@link Authentication}. {@code
 *   studentIdFromQr} is always {@code null} on the {@link CheckInCommand}
 *   built here — the use case derives the real student id by parsing
 *   {@code qrCredentials}.</li>
 * </ul>
 */
@RestController
@PhysicalCheckInEndpoint
public class PhysicalCheckInController {

    private final IssuePhysicalAccessQrUseCase issuePhysicalAccessQrUseCase;
    private final ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase;

    public PhysicalCheckInController(
        IssuePhysicalAccessQrUseCase issuePhysicalAccessQrUseCase,
        ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase
    ) {
        this.issuePhysicalAccessQrUseCase = issuePhysicalAccessQrUseCase;
        this.processPhysicalCheckInUseCase = processPhysicalCheckInUseCase;
    }

    @PostMapping("/api/v1/physical/sessions/{sessionId}/access-qr")
    public ResponseEntity<AccessQrResponse> issueAccessQr(
        @PathVariable String sessionId, Authentication authentication
    ) {
        AccessQrView view =
            issuePhysicalAccessQrUseCase.issue(sessionId, actingUserId(authentication));
        return ResponseEntity.ok(AccessQrResponse.from(view));
    }

    @PostMapping("/api/v1/physical/sessions/{sessionId}/check-ins")
    public ResponseEntity<CheckInResponse> checkIn(
        @PathVariable String sessionId, @Valid @RequestBody CheckInRequest request
    ) {
        CheckInCommand command = new CheckInCommand(
            SessionId.of(sessionId), null, request.qrCredentials(),
            request.deviceId(), request.deviceToken()
        );
        CheckInResult result = processPhysicalCheckInUseCase.checkIn(command);
        HttpStatus status = result.newlyRecorded() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(CheckInResponse.from(result.attendance()));
    }

    private static UUID actingUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
