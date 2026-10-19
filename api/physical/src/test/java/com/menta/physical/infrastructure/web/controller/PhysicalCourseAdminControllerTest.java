package com.menta.physical.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.CreatePhysicalCourseCommand;
import com.menta.physical.application.dto.PhysicalCourseManagementResult;
import com.menta.physical.application.dto.UpdatePhysicalCourseCommand;
import com.menta.physical.application.port.in.CreatePhysicalCourseUseCase;
import com.menta.physical.application.port.in.ListManagedPhysicalCoursesUseCase;
import com.menta.physical.application.port.in.UpdatePhysicalCourseUseCase;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import com.menta.physical.infrastructure.web.dto.CreatePhysicalCourseRequest;
import com.menta.physical.infrastructure.web.dto.PhysicalCourseManagementListResponse;
import com.menta.physical.infrastructure.web.dto.PhysicalCourseManagementResponse;
import com.menta.physical.infrastructure.web.dto.UpdatePhysicalCourseRequest;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class PhysicalCourseAdminControllerTest {

    private CreatePhysicalCourseUseCase createUseCase;
    private ListManagedPhysicalCoursesUseCase listUseCase;
    private UpdatePhysicalCourseUseCase updateUseCase;
    private PhysicalCourseAdminController controller;

    @BeforeEach
    void setUp() {
        createUseCase = mock(CreatePhysicalCourseUseCase.class);
        listUseCase = mock(ListManagedPhysicalCoursesUseCase.class);
        updateUseCase = mock(UpdatePhysicalCourseUseCase.class);
        controller = new PhysicalCourseAdminController(createUseCase, listUseCase, updateUseCase);
    }

    private static Authentication authOf(UUID userId, String role) {
        return new UsernamePasswordAuthenticationToken(
            userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    private static PhysicalCourseManagementResult result() {
        return new PhysicalCourseManagementResult(
            UUID.randomUUID().toString(), "Salsa inicial", "desc", UUID.randomUUID().toString(),
            "María García", "WEDNESDAY", "20:00", 60, "INTERMEDIATE", 20, "ACTIVE"
        );
    }

    @Test
    void create_resolves_admin_from_the_role_authority_and_returns_201() {
        UUID adminId = UUID.randomUUID();
        when(createUseCase.create(any(), eq(adminId), eq(true))).thenReturn(result());
        CreatePhysicalCourseRequest request = new CreatePhysicalCourseRequest(
            "Salsa inicial", "desc", null, "María García", DayOfWeek.WEDNESDAY, LocalTime.of(20, 0), 60,
            PhysicalCourseLevel.INTERMEDIATE, 20
        );

        ResponseEntity<PhysicalCourseManagementResponse> response =
            controller.create(request, authOf(adminId, "ADMIN"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(createUseCase).create(any(), eq(adminId), eq(true));
    }

    @Test
    void create_resolves_instructor_as_non_admin() {
        UUID instructorId = UUID.randomUUID();
        when(createUseCase.create(any(), eq(instructorId), eq(false))).thenReturn(result());
        CreatePhysicalCourseRequest request = new CreatePhysicalCourseRequest(
            "Salsa inicial", "desc", null, "María García", DayOfWeek.WEDNESDAY, LocalTime.of(20, 0), 60,
            PhysicalCourseLevel.INTERMEDIATE, 20
        );

        controller.create(request, authOf(instructorId, "INSTRUCTOR"));

        verify(createUseCase).create(any(), eq(instructorId), eq(false));
    }

    @Test
    void create_parses_a_supplied_professor_id() {
        UUID adminId = UUID.randomUUID();
        UUID targetProfessor = UUID.randomUUID();
        when(createUseCase.create(any(), any(), anyBoolean())).thenReturn(result());
        CreatePhysicalCourseRequest request = new CreatePhysicalCourseRequest(
            "Salsa inicial", "desc", targetProfessor.toString(), "María García", DayOfWeek.WEDNESDAY,
            LocalTime.of(20, 0), 60, PhysicalCourseLevel.INTERMEDIATE, 20
        );

        controller.create(request, authOf(adminId, "ADMIN"));

        ArgumentCaptor<CreatePhysicalCourseCommand> captor = ArgumentCaptor.forClass(CreatePhysicalCourseCommand.class);
        verify(createUseCase).create(captor.capture(), any(), anyBoolean());
        assertThat(captor.getValue().professorId()).isEqualTo(targetProfessor);
    }

    @Test
    void list_wraps_results_under_a_courses_key() {
        UUID adminId = UUID.randomUUID();
        when(listUseCase.list(adminId, true)).thenReturn(List.of(result()));

        ResponseEntity<com.menta.physical.infrastructure.web.dto.PhysicalCourseManagementListResponse> response =
            controller.list(authOf(adminId, "ADMIN"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().courses()).hasSize(1);
    }

    @Test
    void update_delegates_to_the_use_case_with_a_partial_command() {
        UUID ownerId = UUID.randomUUID();
        String courseId = UUID.randomUUID().toString();
        when(updateUseCase.update(eq(courseId), any(), eq(ownerId), eq(false))).thenReturn(result());
        UpdatePhysicalCourseRequest request =
            new UpdatePhysicalCourseRequest("Nuevo título", null, null, null, null, null, null, null);

        ResponseEntity<PhysicalCourseManagementResponse> response =
            controller.update(courseId, request, authOf(ownerId, "INSTRUCTOR"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        org.mockito.ArgumentCaptor<UpdatePhysicalCourseCommand> captor =
            org.mockito.ArgumentCaptor.forClass(UpdatePhysicalCourseCommand.class);
        verify(updateUseCase).update(eq(courseId), captor.capture(), eq(ownerId), eq(false));
        assertThat(captor.getValue().title()).contains("Nuevo título");
        assertThat(captor.getValue().capacity()).isEmpty();
    }

    @Test
    void update_maps_status_when_present() {
        UUID adminId = UUID.randomUUID();
        String courseId = UUID.randomUUID().toString();
        when(updateUseCase.update(eq(courseId), any(), eq(adminId), eq(true))).thenReturn(result());
        UpdatePhysicalCourseRequest request =
            new UpdatePhysicalCourseRequest(null, null, null, null, null, null, null, CourseStatus.INACTIVE);

        controller.update(courseId, request, authOf(adminId, "ADMIN"));

        org.mockito.ArgumentCaptor<UpdatePhysicalCourseCommand> captor =
            org.mockito.ArgumentCaptor.forClass(UpdatePhysicalCourseCommand.class);
        verify(updateUseCase).update(eq(courseId), captor.capture(), eq(adminId), eq(true));
        assertThat(captor.getValue().status()).contains(CourseStatus.INACTIVE);
    }
}
