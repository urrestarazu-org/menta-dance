package com.menta.virtual.infrastructure.web.controller;

import com.menta.virtual.application.dto.UpdateVirtualLessonCommand;
import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import com.menta.virtual.application.port.in.UpdateVirtualLessonUseCase;
import com.menta.virtual.infrastructure.web.dto.UpdateVirtualLessonRequest;
import com.menta.virtual.infrastructure.web.dto.VirtualLessonManagementResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for virtual lesson management (US-VIRTUAL-006). Lives under a
 * DIFFERENT top-level prefix than {@link VirtualCourseAdminController}/{@link
 * VirtualModuleAdminController} ({@code /admin/virtual/lessons/**}) — {@code
 * SecurityConfig} needs its own matcher for it.
 */
@RestController
@RequestMapping("/api/v1/admin/virtual/lessons")
@VirtualManagementEndpoint
public class VirtualLessonAdminController {

    private final UpdateVirtualLessonUseCase updateVirtualLessonUseCase;

    public VirtualLessonAdminController(UpdateVirtualLessonUseCase updateVirtualLessonUseCase) {
        this.updateVirtualLessonUseCase = updateVirtualLessonUseCase;
    }

    @PutMapping("/{lessonId}")
    public ResponseEntity<VirtualLessonManagementResponse> update(
        @PathVariable String lessonId, @RequestBody UpdateVirtualLessonRequest request,
        Authentication authentication
    ) {
        UpdateVirtualLessonCommand command = new UpdateVirtualLessonCommand(
            Optional.ofNullable(request.title()),
            Optional.ofNullable(request.description()),
            Optional.ofNullable(request.videoId()),
            Optional.ofNullable(request.durationMinutes()),
            Optional.ofNullable(request.free()),
            Optional.ofNullable(request.order())
        );
        VirtualLessonManagementResult result = updateVirtualLessonUseCase.update(
            lessonId, command, actingUserId(authentication), isAdmin(authentication)
        );
        return ResponseEntity.ok(VirtualLessonManagementResponse.from(result));
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
