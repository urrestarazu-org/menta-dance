package com.menta.virtual.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import com.menta.virtual.application.port.in.UpdateVirtualLessonUseCase;
import com.menta.virtual.infrastructure.web.dto.UpdateVirtualLessonRequest;
import com.menta.virtual.infrastructure.web.dto.VirtualLessonManagementResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class VirtualLessonAdminControllerTest {

    private final UpdateVirtualLessonUseCase updateUseCase = mock(UpdateVirtualLessonUseCase.class);
    private final VirtualLessonAdminController controller = new VirtualLessonAdminController(updateUseCase);

    private static Authentication authOf(UUID userId, String role) {
        return new UsernamePasswordAuthenticationToken(
            userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    @Test
    void update_returns_200() {
        UUID ownerId = UUID.randomUUID();
        String lessonId = UUID.randomUUID().toString();
        VirtualLessonManagementResult result = new VirtualLessonManagementResult(
            lessonId, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "L1", "d", "v", 10, false, 0
        );
        when(updateUseCase.update(eq(lessonId), any(), eq(ownerId), eq(false))).thenReturn(result);

        ResponseEntity<VirtualLessonManagementResponse> response = controller.update(
            lessonId, new UpdateVirtualLessonRequest(null, null, null, null, null, null),
            authOf(ownerId, "INSTRUCTOR")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
