package com.menta.virtual.infrastructure.web.controller;

import com.menta.virtual.application.dto.CreateVirtualLessonCommand;
import com.menta.virtual.application.dto.UpdateVirtualModuleCommand;
import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.in.CreateVirtualLessonUseCase;
import com.menta.virtual.application.port.in.UpdateVirtualModuleUseCase;
import com.menta.virtual.infrastructure.web.dto.CreateVirtualLessonRequest;
import com.menta.virtual.infrastructure.web.dto.UpdateVirtualModuleRequest;
import com.menta.virtual.infrastructure.web.dto.VirtualLessonManagementResponse;
import com.menta.virtual.infrastructure.web.dto.VirtualModuleManagementResponse;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for virtual module management (US-VIRTUAL-006). Lives under a
 * DIFFERENT top-level prefix than {@link VirtualCourseAdminController}
 * ({@code /admin/virtual/modules/**} vs {@code /admin/virtual/courses/**}) —
 * {@code SecurityConfig} needs its own matcher for it.
 */
@RestController
@RequestMapping("/api/v1/admin/virtual/modules")
@VirtualManagementEndpoint
public class VirtualModuleAdminController {

    private final UpdateVirtualModuleUseCase updateVirtualModuleUseCase;
    private final CreateVirtualLessonUseCase createVirtualLessonUseCase;

    public VirtualModuleAdminController(
        UpdateVirtualModuleUseCase updateVirtualModuleUseCase, CreateVirtualLessonUseCase createVirtualLessonUseCase
    ) {
        this.updateVirtualModuleUseCase = updateVirtualModuleUseCase;
        this.createVirtualLessonUseCase = createVirtualLessonUseCase;
    }

    @PutMapping("/{moduleId}")
    public ResponseEntity<VirtualModuleManagementResponse> update(
        @PathVariable String moduleId, @RequestBody UpdateVirtualModuleRequest request,
        Authentication authentication
    ) {
        UpdateVirtualModuleCommand command =
            new UpdateVirtualModuleCommand(
                Optional.ofNullable(request.title()), Optional.ofNullable(request.order()), Optional.ofNullable(request.preview())
            );
        VirtualModuleManagementResult result = updateVirtualModuleUseCase.update(
            moduleId, command, actingUserId(authentication), isAdmin(authentication)
        );
        return ResponseEntity.ok(VirtualModuleManagementResponse.from(result));
    }

    @PostMapping("/{moduleId}/lessons")
    public ResponseEntity<VirtualLessonManagementResponse> createLesson(
        @PathVariable String moduleId, @Valid @RequestBody CreateVirtualLessonRequest request,
        Authentication authentication
    ) {
        CreateVirtualLessonCommand command = new CreateVirtualLessonCommand(
            request.title(), request.description(), request.videoId(), request.durationMinutes(),
            request.free(), Optional.ofNullable(request.order())
        );
        VirtualLessonManagementResult result = createVirtualLessonUseCase.create(
            moduleId, command, actingUserId(authentication), isAdmin(authentication)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(VirtualLessonManagementResponse.from(result));
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
