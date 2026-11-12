package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.application.port.in.ListPhysicalSessionsByRangeUseCase;
import com.menta.physical.infrastructure.web.dto.PhysicalRangeSessionsResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/admin/physical/sessions?from=&to=} — the sessions that start in the half-open
 * instant range {@code [from, to)} across every course, for staff (#355). {@code SecurityConfig} admits
 * ADMIN, INSTRUCTOR and RECEPTIONIST on this exact method and path; the listing is read-only and not
 * narrowed by course ownership, so this controller never inspects the caller.
 *
 * <p>Every backend date is UTC: the client converts its user's local day to the range. Both bounds are
 * parsed here strictly with {@link Instant#parse}, i.e. ISO-8601 with seconds and a {@code Z} or numeric
 * offset ({@code 2026-11-12T00:00:00-03:00}), which is normalised to the same UTC instant; a {@code +}
 * offset must be URL-encoded as {@code %2B}. A missing or malformed bound, an empty or inverted range
 * or one wider than 7 days becomes a {@code 400 INVALID_REQUEST} problem in {@link
 * PhysicalCourseExceptionHandler}.
 */
@RestController
@PhysicalManagementEndpoint
@RequiredArgsConstructor
public class PhysicalRangeSessionsController {

    private final ListPhysicalSessionsByRangeUseCase listPhysicalSessionsByRangeUseCase;

    @GetMapping("/api/v1/admin/physical/sessions")
    public ResponseEntity<PhysicalRangeSessionsResponse> byRange(
        @RequestParam String from, @RequestParam String to
    ) {
        return ResponseEntity.ok(PhysicalRangeSessionsResponse.from(
            listPhysicalSessionsByRangeUseCase.list(Instant.parse(from), Instant.parse(to))
        ));
    }
}
