package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.application.port.in.GetPhysicalSessionRosterUseCase;
import com.menta.physical.infrastructure.web.dto.PhysicalSessionRosterResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/admin/physical/sessions/{sessionId}/roster} — the students with a confirmed assignment for
 * a session and whether they already checked in, for staff (#355). {@code SecurityConfig} admits ADMIN,
 * INSTRUCTOR and RECEPTIONIST on this exact method and path; the read is not narrowed by course ownership, so
 * this controller never inspects the caller. A malformed {@code sessionId} becomes {@code 400 INVALID_REQUEST}
 * and an unknown session {@code 404 SESSION_NOT_FOUND} in {@link PhysicalCourseExceptionHandler}.
 */
@RestController
@PhysicalManagementEndpoint
@RequiredArgsConstructor
public class PhysicalSessionRosterController {

    private final GetPhysicalSessionRosterUseCase getPhysicalSessionRosterUseCase;

    @GetMapping("/api/v1/admin/physical/sessions/{sessionId}/roster")
    public ResponseEntity<PhysicalSessionRosterResponse> roster(@PathVariable String sessionId) {
        return ResponseEntity.ok(PhysicalSessionRosterResponse.from(getPhysicalSessionRosterUseCase.get(sessionId)));
    }
}
