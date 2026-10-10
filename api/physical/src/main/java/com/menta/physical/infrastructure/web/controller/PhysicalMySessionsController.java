package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.application.port.in.ListMyPhysicalSessionsUseCase;
import com.menta.physical.infrastructure.web.dto.MySessionsResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/physical/sessions/me} — the acting student's upcoming sessions (#356). Like
 * {@code attendance/me}, the endpoint declares no {@code studentId} parameter: the subject is
 * always the token's.
 */
@RestController
@RequiredArgsConstructor
public class PhysicalMySessionsController {

    private final ListMyPhysicalSessionsUseCase listMyPhysicalSessionsUseCase;

    @GetMapping("/api/v1/physical/sessions/me")
    public ResponseEntity<MySessionsResponse> me(Authentication authentication) {
        UUID studentId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(MySessionsResponse.from(listMyPhysicalSessionsUseCase.list(studentId)));
    }
}
