package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.application.dto.AccessQrView;
import com.menta.physical.application.dto.CheckInActor;
import com.menta.physical.application.dto.CheckInCommand;
import com.menta.physical.application.dto.CheckInResult;
import com.menta.physical.application.port.in.IssuePhysicalAccessQrUseCase;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.infrastructure.web.dto.AccessQrResponse;
import com.menta.physical.infrastructure.web.dto.CheckInRequest;
import com.menta.physical.infrastructure.web.dto.CheckInResponse;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for the physical check-in flow (US-PHYSICAL-001,
 * US-PHYSICAL-008). Two endpoints with deliberately different authorization
 * models:
 *
 * <ul>
 *   <li>{@code POST /api/v1/physical/sessions/{sessionId}/access-qr} requires
 *   an authenticated student — {@code SecurityConfig} enforces
 *   {@code .authenticated()} with no specific role, and the student's id is
 *   always taken from the JWT principal, never the request body.</li>
 *   <li>{@code POST /api/v1/physical/sessions/{sessionId}/check-ins} is
 *   {@code permitAll()} at the filter level, so authorization for both its
 *   variants happens inside the use case, not the filter chain (D5). The QR
 *   variant's door reader authenticates with a shared {@code deviceToken}
 *   the use case itself verifies, and its {@link CheckInCommand} always
 *   carries {@code studentId == null} and {@link CheckInActor#anonymous()}.
 *   The MANUAL variant (#45, US-PHYSICAL-008) is receptionist-initiated, so
 *   this endpoint reads {@link Authentication} to resolve the acting
 *   {@link CheckInActor}, stripping the Spring Security {@code ROLE_}
 *   prefix so the application layer never learns that convention.</li>
 * </ul>
 */
@RestController
@PhysicalCheckInEndpoint
public class PhysicalCheckInController {

    private static final String ROLE_PREFIX = "ROLE_";

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
        @PathVariable String sessionId, @Valid @RequestBody CheckInRequest request,
        Authentication authentication
    ) {
        CheckInCommand command = command(SessionId.of(sessionId), request, authentication);
        CheckInResult result = switch (command.type()) {
            case QR -> processPhysicalCheckInUseCase.checkIn(command);
            case MANUAL -> processPhysicalCheckInUseCase.checkInManually(command);
        };
        HttpStatus status = result.newlyRecorded() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(CheckInResponse.from(result.attendance()));
    }

    private static CheckInCommand command(
        SessionId sessionId, CheckInRequest request, Authentication authentication
    ) {
        if ("MANUAL".equals(request.type())) {
            return CheckInCommand.manual(
                sessionId, UUID.fromString(request.studentId()), checkInActor(authentication)
            );
        }
        return CheckInCommand.qr(
            sessionId, request.qrCredentials(), request.deviceId(), request.deviceToken()
        );
    }

    private static CheckInActor checkInActor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            return CheckInActor.anonymous();
        }
        Set<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(name -> name.startsWith(ROLE_PREFIX))
            .map(name -> name.substring(ROLE_PREFIX.length()))
            .collect(Collectors.toUnmodifiableSet());
        return new CheckInActor(UUID.fromString(authentication.getName()), roles);
    }

    private static UUID actingUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
