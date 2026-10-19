package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.application.dto.AttendanceHistoryView;
import com.menta.physical.application.dto.AttendanceViewer;
import com.menta.physical.application.port.in.GetPhysicalAttendanceHistoryUseCase;
import com.menta.physical.infrastructure.web.dto.AttendanceHistoryResponse;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/physical/attendance/me} — the acting student's own month (#39,
 * US-PHYSICAL-002, design C7). No {@code studentId} parameter exists on this endpoint at all:
 * the subject is always {@code actingUserId(authentication)}, so a caller has no parameter to
 * abuse to read another student's data.
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
        @RequestParam String month,
        @RequestParam(required = false, defaultValue = "false") boolean includeAbsent,
        Authentication authentication
    ) {
        AttendanceViewer viewer = AttendanceViewer.self(actingUserId(authentication));
        AttendanceHistoryView view =
            getPhysicalAttendanceHistoryUseCase.history(viewer, YearMonth.parse(month), includeAbsent);
        return ResponseEntity.ok(AttendanceHistoryResponse.from(view));
    }

    private static UUID actingUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
