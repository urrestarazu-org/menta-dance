package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.application.dto.BatchCreatePhysicalSessionsCommand;
import com.menta.physical.application.dto.CreatePhysicalSessionCommand;
import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import com.menta.physical.application.dto.UpdatePhysicalSessionCommand;
import com.menta.physical.application.port.in.BatchCreatePhysicalSessionsUseCase;
import com.menta.physical.application.port.in.CreatePhysicalSessionUseCase;
import com.menta.physical.application.port.in.ListManagedPhysicalSessionsUseCase;
import com.menta.physical.application.port.in.UpdatePhysicalSessionUseCase;
import com.menta.physical.infrastructure.web.dto.BatchCreatePhysicalSessionsRequest;
import com.menta.physical.infrastructure.web.dto.CreatePhysicalSessionRequest;
import com.menta.physical.infrastructure.web.dto.PhysicalSessionManagementListResponse;
import com.menta.physical.infrastructure.web.dto.PhysicalSessionManagementResponse;
import com.menta.physical.infrastructure.web.dto.UpdatePhysicalSessionRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for physical session management (US-PHYSICAL-006). Creation,
 * batch creation and listing live under {@code
 * /admin/physical/courses/{courseId}/sessions} — already covered by #42's
 * {@code hasAnyRole(ADMIN, INSTRUCTOR)} matcher. Updating a session uses a
 * different top-level prefix, {@code /admin/physical/sessions/{sessionId}},
 * which needs its own matcher (see {@code SecurityConfig}). This class never
 * checks the role itself for the coarse gate — only ownership via the
 * parent course, a per-resource concern the use cases enforce.
 */
@RestController
@PhysicalManagementEndpoint
public class PhysicalSessionAdminController {

    /** ~10 years ahead: comfortably inside MySQL's DATETIME range, generous for an unbounded "to". */
    private static final Duration DEFAULT_RANGE = Duration.ofDays(3650);

    private final CreatePhysicalSessionUseCase createPhysicalSessionUseCase;
    private final BatchCreatePhysicalSessionsUseCase batchCreatePhysicalSessionsUseCase;
    private final ListManagedPhysicalSessionsUseCase listManagedPhysicalSessionsUseCase;
    private final UpdatePhysicalSessionUseCase updatePhysicalSessionUseCase;

    public PhysicalSessionAdminController(
        CreatePhysicalSessionUseCase createPhysicalSessionUseCase,
        BatchCreatePhysicalSessionsUseCase batchCreatePhysicalSessionsUseCase,
        ListManagedPhysicalSessionsUseCase listManagedPhysicalSessionsUseCase,
        UpdatePhysicalSessionUseCase updatePhysicalSessionUseCase
    ) {
        this.createPhysicalSessionUseCase = createPhysicalSessionUseCase;
        this.batchCreatePhysicalSessionsUseCase = batchCreatePhysicalSessionsUseCase;
        this.listManagedPhysicalSessionsUseCase = listManagedPhysicalSessionsUseCase;
        this.updatePhysicalSessionUseCase = updatePhysicalSessionUseCase;
    }

    @PostMapping("/api/v1/admin/physical/courses/{courseId}/sessions")
    public ResponseEntity<PhysicalSessionManagementResponse> create(
        @PathVariable String courseId, @Valid @RequestBody CreatePhysicalSessionRequest request,
        Authentication authentication
    ) {
        CreatePhysicalSessionCommand command = new CreatePhysicalSessionCommand(
            request.date(), request.startTime(), request.capacity(), request.notes()
        );
        PhysicalSessionManagementResult result = createPhysicalSessionUseCase.create(
            courseId, command, actingUserId(authentication), isAdmin(authentication)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(PhysicalSessionManagementResponse.from(result));
    }

    @PostMapping("/api/v1/admin/physical/courses/{courseId}/sessions/batch")
    public ResponseEntity<PhysicalSessionManagementListResponse> createBatch(
        @PathVariable String courseId, @Valid @RequestBody BatchCreatePhysicalSessionsRequest request,
        Authentication authentication
    ) {
        BatchCreatePhysicalSessionsCommand command =
            new BatchCreatePhysicalSessionsCommand(request.fromDate(), request.toDate());
        List<PhysicalSessionManagementResult> results = batchCreatePhysicalSessionsUseCase.createBatch(
            courseId, command, actingUserId(authentication), isAdmin(authentication)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(new PhysicalSessionManagementListResponse(
            results.stream().map(PhysicalSessionManagementResponse::from).toList()
        ));
    }

    @GetMapping("/api/v1/admin/physical/courses/{courseId}/sessions")
    public ResponseEntity<PhysicalSessionManagementListResponse> list(
        @PathVariable String courseId,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        Authentication authentication
    ) {
        Instant now = Instant.now();
        Instant effectiveFrom = from != null ? from : now.minus(DEFAULT_RANGE);
        Instant effectiveTo = to != null ? to : now.plus(DEFAULT_RANGE);
        List<PhysicalSessionManagementResult> results = listManagedPhysicalSessionsUseCase.list(
            courseId, effectiveFrom, effectiveTo, actingUserId(authentication), isAdmin(authentication)
        );
        return ResponseEntity.ok(new PhysicalSessionManagementListResponse(
            results.stream().map(PhysicalSessionManagementResponse::from).toList()
        ));
    }

    @PatchMapping("/api/v1/admin/physical/sessions/{sessionId}")
    public ResponseEntity<PhysicalSessionManagementResponse> update(
        @PathVariable String sessionId, @Valid @RequestBody UpdatePhysicalSessionRequest request,
        Authentication authentication
    ) {
        UpdatePhysicalSessionCommand command = new UpdatePhysicalSessionCommand(
            Optional.ofNullable(request.date()),
            Optional.ofNullable(request.startTime()),
            Optional.ofNullable(request.capacity()),
            Optional.ofNullable(request.notes()),
            Optional.ofNullable(request.status())
        );
        PhysicalSessionManagementResult result = updatePhysicalSessionUseCase.update(
            sessionId, command, actingUserId(authentication), isAdmin(authentication)
        );
        return ResponseEntity.ok(PhysicalSessionManagementResponse.from(result));
    }

    private static UUID actingUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals);
    }
}
