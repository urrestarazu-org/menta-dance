package com.menta.physical.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import com.menta.physical.application.port.in.BatchCreatePhysicalSessionsUseCase;
import com.menta.physical.application.port.in.CreatePhysicalSessionUseCase;
import com.menta.physical.application.port.in.ListManagedPhysicalSessionsUseCase;
import com.menta.physical.application.port.in.UpdatePhysicalSessionUseCase;
import com.menta.physical.infrastructure.web.dto.BatchCreatePhysicalSessionsRequest;
import com.menta.physical.infrastructure.web.dto.CreatePhysicalSessionRequest;
import com.menta.physical.infrastructure.web.dto.PhysicalSessionManagementListResponse;
import com.menta.physical.infrastructure.web.dto.PhysicalSessionManagementResponse;
import com.menta.physical.infrastructure.web.dto.UpdatePhysicalSessionRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class PhysicalSessionAdminControllerTest {

    private CreatePhysicalSessionUseCase createUseCase;
    private BatchCreatePhysicalSessionsUseCase batchUseCase;
    private ListManagedPhysicalSessionsUseCase listUseCase;
    private UpdatePhysicalSessionUseCase updateUseCase;
    private PhysicalSessionAdminController controller;

    @BeforeEach
    void setUp() {
        createUseCase = mock(CreatePhysicalSessionUseCase.class);
        batchUseCase = mock(BatchCreatePhysicalSessionsUseCase.class);
        listUseCase = mock(ListManagedPhysicalSessionsUseCase.class);
        updateUseCase = mock(UpdatePhysicalSessionUseCase.class);
        controller = new PhysicalSessionAdminController(createUseCase, batchUseCase, listUseCase, updateUseCase);
    }

    private static Authentication authOf(UUID userId, String role) {
        return new UsernamePasswordAuthenticationToken(
            userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    private static PhysicalSessionManagementResult result() {
        return new PhysicalSessionManagementResult(
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), "2026-09-15T22:00:00Z",
            20, 0, 0, 20, "SCHEDULED", null
        );
    }

    @Test
    void create_resolves_admin_from_the_role_authority_and_returns_201() {
        UUID adminId = UUID.randomUUID();
        String courseId = UUID.randomUUID().toString();
        when(createUseCase.create(eq(courseId), any(), eq(adminId), eq(true))).thenReturn(result());

        ResponseEntity<PhysicalSessionManagementResponse> response = controller.create(
            courseId, new CreatePhysicalSessionRequest(LocalDate.of(2026, 9, 15), LocalTime.of(19, 0), null, null),
            authOf(adminId, "ADMIN")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void create_batch_resolves_instructor_and_returns_the_generated_sessions() {
        UUID instructorId = UUID.randomUUID();
        String courseId = UUID.randomUUID().toString();
        when(batchUseCase.createBatch(eq(courseId), any(), eq(instructorId), eq(false)))
            .thenReturn(List.of(result(), result()));

        ResponseEntity<PhysicalSessionManagementListResponse> response = controller.createBatch(
            courseId, new BatchCreatePhysicalSessionsRequest(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
            authOf(instructorId, "INSTRUCTOR")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().sessions()).hasSize(2);
    }

    @Test
    void list_defaults_from_and_to_when_not_provided() {
        UUID adminId = UUID.randomUUID();
        String courseId = UUID.randomUUID().toString();
        when(listUseCase.list(eq(courseId), any(), any(), eq(adminId), eq(true))).thenReturn(List.of(result()));

        ResponseEntity<PhysicalSessionManagementListResponse> response =
            controller.list(courseId, null, null, authOf(adminId, "ADMIN"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().sessions()).hasSize(1);
    }

    @Test
    void update_returns_200_with_the_patched_session() {
        UUID ownerId = UUID.randomUUID();
        String sessionId = UUID.randomUUID().toString();
        when(updateUseCase.update(eq(sessionId), any(), eq(ownerId), eq(false))).thenReturn(result());

        ResponseEntity<PhysicalSessionManagementResponse> response = controller.update(
            sessionId, new UpdatePhysicalSessionRequest(null, null, null, null, null),
            authOf(ownerId, "INSTRUCTOR")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
