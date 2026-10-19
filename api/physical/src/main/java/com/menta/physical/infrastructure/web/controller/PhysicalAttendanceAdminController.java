package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.application.dto.AttendanceHistoryView;
import com.menta.physical.application.dto.AttendanceViewer;
import com.menta.physical.application.port.in.GetPhysicalAttendanceHistoryUseCase;
import com.menta.physical.infrastructure.web.dto.AttendanceHistoryResponse;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/admin/physical/attendance/{studentId}} — elevated read of ANOTHER student's
 * month (#39, US-PHYSICAL-002, design C2/C7). {@code SecurityConfig}'s dedicated matcher admits
 * both {@code ADMIN} and {@code INSTRUCTOR}; this controller never checks the role itself for the
 * coarse gate — it only builds {@link AttendanceViewer#elevated} from the path variable plus the
 * principal. The instructor-ownership narrowing already happens inside the query (design C1), so
 * an {@code INSTRUCTOR} reaches this controller unconditionally and never gets a {@code 403}
 * here. Reuses {@link AttendanceHistoryResponse}, the same shape the self controller returns —
 * deliberately, so a shape difference never becomes an enumeration oracle (design C4/C6).
 */
@RestController
@PhysicalAttendanceEndpoint
public class PhysicalAttendanceAdminController {

    private final GetPhysicalAttendanceHistoryUseCase getPhysicalAttendanceHistoryUseCase;

    public PhysicalAttendanceAdminController(
        GetPhysicalAttendanceHistoryUseCase getPhysicalAttendanceHistoryUseCase
    ) {
        this.getPhysicalAttendanceHistoryUseCase = getPhysicalAttendanceHistoryUseCase;
    }

    @GetMapping("/api/v1/admin/physical/attendance/{studentId}")
    public ResponseEntity<AttendanceHistoryResponse> forStudent(
        @PathVariable String studentId,
        @RequestParam String month,
        @RequestParam(required = false, defaultValue = "false") boolean includeAbsent,
        Authentication authentication
    ) {
        AttendanceViewer viewer = AttendanceViewer.elevated(
            UUID.fromString(studentId), actingUserId(authentication), isAdmin(authentication)
        );
        AttendanceHistoryView view =
            getPhysicalAttendanceHistoryUseCase.history(viewer, YearMonth.parse(month), includeAbsent);
        return ResponseEntity.ok(AttendanceHistoryResponse.from(view));
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
