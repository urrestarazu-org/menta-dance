package com.menta.virtual.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.in.CreateVirtualLessonUseCase;
import com.menta.virtual.application.port.in.UpdateVirtualModuleUseCase;
import com.menta.virtual.infrastructure.web.dto.CreateVirtualLessonRequest;
import com.menta.virtual.infrastructure.web.dto.UpdateVirtualModuleRequest;
import com.menta.virtual.infrastructure.web.dto.VirtualLessonManagementResponse;
import com.menta.virtual.infrastructure.web.dto.VirtualModuleManagementResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class VirtualModuleAdminControllerTest {

    private final UpdateVirtualModuleUseCase updateUseCase = mock(UpdateVirtualModuleUseCase.class);
    private final CreateVirtualLessonUseCase createLessonUseCase = mock(CreateVirtualLessonUseCase.class);
    private final VirtualModuleAdminController controller =
        new VirtualModuleAdminController(updateUseCase, createLessonUseCase);

    private static Authentication authOf(UUID userId, String role) {
        return new UsernamePasswordAuthenticationToken(
            userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    @Test
    void update_returns_200() {
        UUID ownerId = UUID.randomUUID();
        String moduleId = UUID.randomUUID().toString();
        VirtualModuleManagementResult result =
            new VirtualModuleManagementResult(moduleId, UUID.randomUUID().toString(), "M1", 0);
        when(updateUseCase.update(eq(moduleId), any(), eq(ownerId), eq(false))).thenReturn(result);

        ResponseEntity<VirtualModuleManagementResponse> response = controller.update(
            moduleId, new UpdateVirtualModuleRequest(null, null), authOf(ownerId, "INSTRUCTOR")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void create_lesson_returns_201() {
        UUID ownerId = UUID.randomUUID();
        String moduleId = UUID.randomUUID().toString();
        VirtualLessonManagementResult result = new VirtualLessonManagementResult(
            UUID.randomUUID().toString(), moduleId, UUID.randomUUID().toString(), "L1", "d", "v", 10, false, 0
        );
        when(createLessonUseCase.create(eq(moduleId), any(), eq(ownerId), eq(false))).thenReturn(result);

        ResponseEntity<VirtualLessonManagementResponse> response = controller.createLesson(
            moduleId, new CreateVirtualLessonRequest("L1", "d", "v", 10, false, null),
            authOf(ownerId, "INSTRUCTOR")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
