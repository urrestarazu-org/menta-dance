package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.application.port.in.ListMyPhysicalSessionsUseCase;
import com.menta.physical.infrastructure.web.dto.MySessionsResponse;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/physical/sessions/me?from=&to=} — the acting student's sessions that start in the
 * half-open instant range {@code [from, to)} (#356, #729). Like {@code attendance/me}, the endpoint
 * declares no {@code studentId} parameter: the subject is always the token's.
 *
 * <p>Every backend date is UTC: the client converts its user's local day or month to the range. Both
 * bounds are parsed here strictly with {@link Instant#parse}, i.e. ISO-8601 with seconds and a {@code Z}
 * or numeric offset, which is normalised to the same UTC instant; a {@code +} offset must be URL-encoded
 * as {@code %2B}. A missing or malformed bound, an empty or inverted range or one wider than 92 days
 * becomes a {@code 400 INVALID_REQUEST} problem in {@link PhysicalAttendanceExceptionHandler}.
 */
@RestController
@PhysicalAttendanceEndpoint
@RequiredArgsConstructor
public class PhysicalMySessionsController {

    private final ListMyPhysicalSessionsUseCase listMyPhysicalSessionsUseCase;

    @GetMapping("/api/v1/physical/sessions/me")
    public ResponseEntity<MySessionsResponse> me(
        @RequestParam String from, @RequestParam String to, Authentication authentication
    ) {
        UUID studentId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(MySessionsResponse.from(
            listMyPhysicalSessionsUseCase.list(studentId, Instant.parse(from), Instant.parse(to))
        ));
    }
}
