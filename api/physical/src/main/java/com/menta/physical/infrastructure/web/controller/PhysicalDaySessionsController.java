package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.application.port.in.ListPhysicalSessionsByDateUseCase;
import com.menta.physical.infrastructure.web.dto.PhysicalDaySessionsResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/admin/physical/sessions?date=YYYY-MM-DD} — the sessions of one academy day
 * across every course, for staff (#355). {@code SecurityConfig} admits ADMIN, INSTRUCTOR and
 * RECEPTIONIST on this exact method and path; the listing is read-only and not narrowed by course
 * ownership, so this controller never inspects the caller.
 *
 * <p>{@code date} is parsed here as a strict ISO local date ({@code LocalDate.parse}); a missing or
 * malformed one becomes a {@code 400 INVALID_REQUEST} problem in {@link PhysicalCourseExceptionHandler}.
 */
@RestController
@PhysicalManagementEndpoint
@RequiredArgsConstructor
public class PhysicalDaySessionsController {

    private final ListPhysicalSessionsByDateUseCase listPhysicalSessionsByDateUseCase;

    @GetMapping("/api/v1/admin/physical/sessions")
    public ResponseEntity<PhysicalDaySessionsResponse> byDate(@RequestParam String date) {
        return ResponseEntity.ok(
            PhysicalDaySessionsResponse.from(listPhysicalSessionsByDateUseCase.list(LocalDate.parse(date)))
        );
    }
}
