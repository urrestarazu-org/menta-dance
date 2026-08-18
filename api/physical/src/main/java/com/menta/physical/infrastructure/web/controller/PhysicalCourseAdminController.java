package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.application.dto.CreatePhysicalCourseCommand;
import com.menta.physical.application.dto.PhysicalCourseManagementResult;
import com.menta.physical.application.dto.UpdatePhysicalCourseCommand;
import com.menta.physical.application.port.in.CreatePhysicalCourseUseCase;
import com.menta.physical.application.port.in.ListManagedPhysicalCoursesUseCase;
import com.menta.physical.application.port.in.UpdatePhysicalCourseUseCase;
import com.menta.physical.infrastructure.web.dto.CreatePhysicalCourseRequest;
import com.menta.physical.infrastructure.web.dto.PhysicalCourseManagementListResponse;
import com.menta.physical.infrastructure.web.dto.PhysicalCourseManagementResponse;
import com.menta.physical.infrastructure.web.dto.UpdatePhysicalCourseRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for physical course management (US-PHYSICAL-005). {@code
 * SecurityConfig} grants ADMIN and INSTRUCTOR access to this prefix; this
 * class never checks the role itself for the coarse gate — only ownership,
 * which is a per-resource concern the use cases enforce.
 */
@RestController
@RequestMapping("/api/v1/admin/physical/courses")
@PhysicalManagementEndpoint
public class PhysicalCourseAdminController {

    private final CreatePhysicalCourseUseCase createPhysicalCourseUseCase;
    private final ListManagedPhysicalCoursesUseCase listManagedPhysicalCoursesUseCase;
    private final UpdatePhysicalCourseUseCase updatePhysicalCourseUseCase;

    public PhysicalCourseAdminController(
        CreatePhysicalCourseUseCase createPhysicalCourseUseCase,
        ListManagedPhysicalCoursesUseCase listManagedPhysicalCoursesUseCase,
        UpdatePhysicalCourseUseCase updatePhysicalCourseUseCase
    ) {
        this.createPhysicalCourseUseCase = createPhysicalCourseUseCase;
        this.listManagedPhysicalCoursesUseCase = listManagedPhysicalCoursesUseCase;
        this.updatePhysicalCourseUseCase = updatePhysicalCourseUseCase;
    }

    @PostMapping
    public ResponseEntity<PhysicalCourseManagementResponse> create(
        @Valid @RequestBody CreatePhysicalCourseRequest request, Authentication authentication
    ) {
        CreatePhysicalCourseCommand command = new CreatePhysicalCourseCommand(
            request.title(), request.description(), parseProfessorId(request.professorId()),
            request.professorName(), request.dayOfWeek(), request.startTime(), request.durationMinutes(),
            request.level(), request.capacity()
        );
        PhysicalCourseManagementResult result = createPhysicalCourseUseCase.create(
            command, actingUserId(authentication), isAdmin(authentication)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(PhysicalCourseManagementResponse.from(result));
    }

    @GetMapping
    public ResponseEntity<PhysicalCourseManagementListResponse> list(Authentication authentication) {
        List<PhysicalCourseManagementResult> results = listManagedPhysicalCoursesUseCase.list(
            actingUserId(authentication), isAdmin(authentication)
        );
        return ResponseEntity.ok(new PhysicalCourseManagementListResponse(
            results.stream().map(PhysicalCourseManagementResponse::from).toList()
        ));
    }

    @PatchMapping("/{courseId}")
    public ResponseEntity<PhysicalCourseManagementResponse> update(
        @PathVariable String courseId, @Valid @RequestBody UpdatePhysicalCourseRequest request,
        Authentication authentication
    ) {
        UpdatePhysicalCourseCommand command = new UpdatePhysicalCourseCommand(
            Optional.ofNullable(request.title()),
            Optional.ofNullable(request.description()),
            Optional.ofNullable(request.dayOfWeek()),
            Optional.ofNullable(request.startTime()),
            Optional.ofNullable(request.durationMinutes()),
            Optional.ofNullable(request.level()),
            Optional.ofNullable(request.capacity()),
            Optional.ofNullable(request.status())
        );
        PhysicalCourseManagementResult result = updatePhysicalCourseUseCase.update(
            courseId, command, actingUserId(authentication), isAdmin(authentication)
        );
        return ResponseEntity.ok(PhysicalCourseManagementResponse.from(result));
    }

    private static UUID parseProfessorId(String professorId) {
        return professorId == null ? null : UUID.fromString(professorId);
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
