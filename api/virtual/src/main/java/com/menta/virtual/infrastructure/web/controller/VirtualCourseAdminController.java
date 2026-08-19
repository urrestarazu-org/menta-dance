package com.menta.virtual.infrastructure.web.controller;

import com.menta.virtual.application.dto.CreateVirtualCourseCommand;
import com.menta.virtual.application.dto.CreateVirtualModuleCommand;
import com.menta.virtual.application.dto.ReorderVirtualModulesCommand;
import com.menta.virtual.application.dto.UpdateVirtualCourseCommand;
import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.in.CreateVirtualCourseUseCase;
import com.menta.virtual.application.port.in.CreateVirtualModuleUseCase;
import com.menta.virtual.application.port.in.DeleteVirtualCourseUseCase;
import com.menta.virtual.application.port.in.ListManagedVirtualCoursesUseCase;
import com.menta.virtual.application.port.in.PublishVirtualCourseUseCase;
import com.menta.virtual.application.port.in.ReorderVirtualModulesUseCase;
import com.menta.virtual.application.port.in.UnpublishVirtualCourseUseCase;
import com.menta.virtual.application.port.in.UpdateVirtualCourseUseCase;
import com.menta.virtual.infrastructure.web.dto.CreateVirtualCourseRequest;
import com.menta.virtual.infrastructure.web.dto.CreateVirtualModuleRequest;
import com.menta.virtual.infrastructure.web.dto.ReorderVirtualModulesRequest;
import com.menta.virtual.infrastructure.web.dto.UpdateVirtualCourseRequest;
import com.menta.virtual.infrastructure.web.dto.VirtualCourseManagementListResponse;
import com.menta.virtual.infrastructure.web.dto.VirtualCourseManagementResponse;
import com.menta.virtual.infrastructure.web.dto.VirtualModuleManagementListResponse;
import com.menta.virtual.infrastructure.web.dto.VirtualModuleManagementResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for virtual course management (US-VIRTUAL-006). {@code
 * SecurityConfig} grants ADMIN and INSTRUCTOR access to this prefix; this
 * class never checks the role itself for the coarse gate — only ownership,
 * which is a per-resource concern the use cases enforce.
 */
@RestController
@RequestMapping("/api/v1/admin/virtual/courses")
@VirtualManagementEndpoint
public class VirtualCourseAdminController {

    private final CreateVirtualCourseUseCase createVirtualCourseUseCase;
    private final ListManagedVirtualCoursesUseCase listManagedVirtualCoursesUseCase;
    private final UpdateVirtualCourseUseCase updateVirtualCourseUseCase;
    private final DeleteVirtualCourseUseCase deleteVirtualCourseUseCase;
    private final PublishVirtualCourseUseCase publishVirtualCourseUseCase;
    private final UnpublishVirtualCourseUseCase unpublishVirtualCourseUseCase;
    private final CreateVirtualModuleUseCase createVirtualModuleUseCase;
    private final ReorderVirtualModulesUseCase reorderVirtualModulesUseCase;

    public VirtualCourseAdminController(
        CreateVirtualCourseUseCase createVirtualCourseUseCase,
        ListManagedVirtualCoursesUseCase listManagedVirtualCoursesUseCase,
        UpdateVirtualCourseUseCase updateVirtualCourseUseCase,
        DeleteVirtualCourseUseCase deleteVirtualCourseUseCase,
        PublishVirtualCourseUseCase publishVirtualCourseUseCase,
        UnpublishVirtualCourseUseCase unpublishVirtualCourseUseCase,
        CreateVirtualModuleUseCase createVirtualModuleUseCase,
        ReorderVirtualModulesUseCase reorderVirtualModulesUseCase
    ) {
        this.createVirtualCourseUseCase = createVirtualCourseUseCase;
        this.listManagedVirtualCoursesUseCase = listManagedVirtualCoursesUseCase;
        this.updateVirtualCourseUseCase = updateVirtualCourseUseCase;
        this.deleteVirtualCourseUseCase = deleteVirtualCourseUseCase;
        this.publishVirtualCourseUseCase = publishVirtualCourseUseCase;
        this.unpublishVirtualCourseUseCase = unpublishVirtualCourseUseCase;
        this.createVirtualModuleUseCase = createVirtualModuleUseCase;
        this.reorderVirtualModulesUseCase = reorderVirtualModulesUseCase;
    }

    @PostMapping
    public ResponseEntity<VirtualCourseManagementResponse> create(
        @Valid @RequestBody CreateVirtualCourseRequest request, Authentication authentication
    ) {
        CreateVirtualCourseCommand command = new CreateVirtualCourseCommand(
            request.title(), request.shortDescription(), request.description(), parseUuid(request.professorId()),
            request.imageUrl(), request.category(), request.level()
        );
        VirtualCourseManagementResult result = createVirtualCourseUseCase.create(
            command, actingUserId(authentication), isAdmin(authentication)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(VirtualCourseManagementResponse.from(result));
    }

    @GetMapping
    public ResponseEntity<VirtualCourseManagementListResponse> list(Authentication authentication) {
        List<VirtualCourseManagementResult> results = listManagedVirtualCoursesUseCase.list(
            actingUserId(authentication), isAdmin(authentication)
        );
        return ResponseEntity.ok(new VirtualCourseManagementListResponse(
            results.stream().map(VirtualCourseManagementResponse::from).toList()
        ));
    }

    @PutMapping("/{courseId}")
    public ResponseEntity<VirtualCourseManagementResponse> update(
        @PathVariable String courseId, @RequestBody UpdateVirtualCourseRequest request,
        Authentication authentication
    ) {
        UpdateVirtualCourseCommand command = new UpdateVirtualCourseCommand(
            Optional.ofNullable(request.title()),
            Optional.ofNullable(request.shortDescription()),
            Optional.ofNullable(request.description()),
            Optional.ofNullable(request.imageUrl()),
            Optional.ofNullable(request.category()),
            Optional.ofNullable(request.level()),
            Optional.ofNullable(request.premium())
        );
        VirtualCourseManagementResult result = updateVirtualCourseUseCase.update(
            courseId, command, actingUserId(authentication), isAdmin(authentication)
        );
        return ResponseEntity.ok(VirtualCourseManagementResponse.from(result));
    }

    @DeleteMapping("/{courseId}")
    public ResponseEntity<Void> delete(@PathVariable String courseId, Authentication authentication) {
        deleteVirtualCourseUseCase.delete(courseId, actingUserId(authentication), isAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{courseId}/publish")
    public ResponseEntity<VirtualCourseManagementResponse> publish(
        @PathVariable String courseId, Authentication authentication
    ) {
        VirtualCourseManagementResult result =
            publishVirtualCourseUseCase.publish(courseId, actingUserId(authentication), isAdmin(authentication));
        return ResponseEntity.ok(VirtualCourseManagementResponse.from(result));
    }

    @PutMapping("/{courseId}/unpublish")
    public ResponseEntity<VirtualCourseManagementResponse> unpublish(
        @PathVariable String courseId, Authentication authentication
    ) {
        VirtualCourseManagementResult result =
            unpublishVirtualCourseUseCase.unpublish(courseId, actingUserId(authentication), isAdmin(authentication));
        return ResponseEntity.ok(VirtualCourseManagementResponse.from(result));
    }

    @PostMapping("/{courseId}/modules")
    public ResponseEntity<VirtualModuleManagementResponse> createModule(
        @PathVariable String courseId, @Valid @RequestBody CreateVirtualModuleRequest request,
        Authentication authentication
    ) {
        CreateVirtualModuleCommand command =
            new CreateVirtualModuleCommand(request.title(), Optional.ofNullable(request.order()));
        VirtualModuleManagementResult result = createVirtualModuleUseCase.create(
            courseId, command, actingUserId(authentication), isAdmin(authentication)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(VirtualModuleManagementResponse.from(result));
    }

    @PutMapping("/{courseId}/modules/reorder")
    public ResponseEntity<VirtualModuleManagementListResponse> reorderModules(
        @PathVariable String courseId, @Valid @RequestBody ReorderVirtualModulesRequest request,
        Authentication authentication
    ) {
        ReorderVirtualModulesCommand command = new ReorderVirtualModulesCommand(request.moduleIds());
        List<VirtualModuleManagementResult> results = reorderVirtualModulesUseCase.reorder(
            courseId, command, actingUserId(authentication), isAdmin(authentication)
        );
        return ResponseEntity.ok(new VirtualModuleManagementListResponse(
            results.stream().map(VirtualModuleManagementResponse::from).toList()
        ));
    }

    private static UUID parseUuid(String value) {
        return value == null ? null : UUID.fromString(value);
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
