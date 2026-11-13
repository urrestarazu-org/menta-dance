package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.application.dto.AttendanceHistoryView;
import com.menta.physical.application.dto.AttendanceViewer;
import com.menta.physical.application.port.in.GetPhysicalAttendanceHistoryUseCase;
import com.menta.physical.infrastructure.web.dto.AttendanceHistoryResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/physical/attendance/me?from=&to=} — the acting student's own attendance in the
 * half-open instant range {@code [from, to)} (#39, #729, US-PHYSICAL-002, design C7). No
 * {@code studentId} parameter exists on this endpoint at all: the subject is always
 * {@code actingUserId(authentication)}, so a caller has no parameter to
 * abuse to read another student's data.
 *
 * <p>Every backend date is UTC: the client converts its user's local month to the range. Both bounds
 * are parsed strictly with {@link Instant#parse} (ISO-8601 with seconds and a {@code Z} or numeric
 * offset, normalised to UTC; a {@code +} offset must be URL-encoded as {@code %2B}). A missing or
 * malformed bound, an empty or inverted range or one wider than 92 days becomes a
 * {@code 400 INVALID_REQUEST} problem in {@link PhysicalAttendanceExceptionHandler}.
 */
@RestController
@PhysicalAttendanceEndpoint
public class PhysicalAttendanceController {

    private final GetPhysicalAttendanceHistoryUseCase getPhysicalAttendanceHistoryUseCase;

    public PhysicalAttendanceController(GetPhysicalAttendanceHistoryUseCase getPhysicalAttendanceHistoryUseCase) {
        this.getPhysicalAttendanceHistoryUseCase = getPhysicalAttendanceHistoryUseCase;
    }

    @GetMapping("/api/v1/physical/attendance/me")
    public ResponseEntity<AttendanceHistoryResponse> me(
        @RequestParam String from,
        @RequestParam String to,
        @RequestParam(required = false, defaultValue = "false") boolean includeAbsent,
        Authentication authentication
    ) {
        AttendanceViewer viewer = AttendanceViewer.self(actingUserId(authentication));
        AttendanceHistoryView view = getPhysicalAttendanceHistoryUseCase.history(
            viewer, Instant.parse(from), Instant.parse(to), includeAbsent
        );
        return ResponseEntity.ok(AttendanceHistoryResponse.from(view));
    }

    private static UUID actingUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
