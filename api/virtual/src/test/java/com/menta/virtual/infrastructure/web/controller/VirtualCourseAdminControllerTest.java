package com.menta.virtual.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.VirtualCourseAdminDetailView;
import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.dto.VirtualCourseStats;
import com.menta.virtual.application.dto.VirtualLessonAdminSummary;
import com.menta.virtual.application.dto.VirtualModuleAdminDetail;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.in.CreateVirtualCourseUseCase;
import com.menta.virtual.application.port.in.CreateVirtualModuleUseCase;
import com.menta.virtual.application.port.in.DeleteVirtualCourseUseCase;
import com.menta.virtual.application.port.in.ListManagedVirtualCoursesUseCase;
import com.menta.virtual.application.port.in.PublishVirtualCourseUseCase;
import com.menta.virtual.application.port.in.ReorderVirtualModulesUseCase;
import com.menta.virtual.application.port.in.UnpublishVirtualCourseUseCase;
import com.menta.virtual.application.port.in.UpdateVirtualCourseUseCase;
import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import com.menta.virtual.domain.exception.CourseNotFoundException;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.infrastructure.web.dto.CreateVirtualCourseRequest;
import com.menta.virtual.infrastructure.web.dto.CreateVirtualModuleRequest;
import com.menta.virtual.infrastructure.web.dto.ReorderVirtualModulesRequest;
import com.menta.virtual.infrastructure.web.dto.UpdateVirtualCourseRequest;
import com.menta.virtual.infrastructure.web.dto.VirtualCourseAdminDetailResponse;
import com.menta.virtual.infrastructure.web.dto.VirtualCourseManagementListResponse;
import com.menta.virtual.infrastructure.web.dto.VirtualCourseManagementResponse;
import com.menta.virtual.infrastructure.web.dto.VirtualModuleManagementListResponse;
import com.menta.virtual.infrastructure.web.dto.VirtualModuleManagementResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class VirtualCourseAdminControllerTest {

    private CreateVirtualCourseUseCase createUseCase;
    private ListManagedVirtualCoursesUseCase listUseCase;
    private UpdateVirtualCourseUseCase updateUseCase;
    private DeleteVirtualCourseUseCase deleteUseCase;
    private PublishVirtualCourseUseCase publishUseCase;
    private UnpublishVirtualCourseUseCase unpublishUseCase;
    private CreateVirtualModuleUseCase createModuleUseCase;
    private ReorderVirtualModulesUseCase reorderUseCase;
    private VirtualCourseCatalogPort catalogPort;
    private VirtualCourseAdminController controller;

    @BeforeEach
    void setUp() {
        createUseCase = mock(CreateVirtualCourseUseCase.class);
        listUseCase = mock(ListManagedVirtualCoursesUseCase.class);
        updateUseCase = mock(UpdateVirtualCourseUseCase.class);
        deleteUseCase = mock(DeleteVirtualCourseUseCase.class);
        publishUseCase = mock(PublishVirtualCourseUseCase.class);
        unpublishUseCase = mock(UnpublishVirtualCourseUseCase.class);
        createModuleUseCase = mock(CreateVirtualModuleUseCase.class);
        reorderUseCase = mock(ReorderVirtualModulesUseCase.class);
        catalogPort = mock(VirtualCourseCatalogPort.class);
        controller = new VirtualCourseAdminController(
            createUseCase, listUseCase, updateUseCase, deleteUseCase, publishUseCase, unpublishUseCase,
            createModuleUseCase, reorderUseCase, catalogPort
        );
    }

    private static Authentication authOf(UUID userId, String role) {
        return new UsernamePasswordAuthenticationToken(
            userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    private static VirtualCourseManagementResult result() {
        return new VirtualCourseManagementResult(
            UUID.randomUUID().toString(), "t", "s", "d", UUID.randomUUID().toString(), "i", "tango",
            "BEGINNER", false, "DRAFT"
        );
    }

    @Test
    void create_resolves_instructor_from_the_role_authority_and_returns_201() {
        UUID instructorId = UUID.randomUUID();
        when(createUseCase.create(any(), eq(instructorId), eq(false))).thenReturn(result());

        ResponseEntity<VirtualCourseManagementResponse> response = controller.create(
            new CreateVirtualCourseRequest("t", "s", "d", null, "i", "tango", "BEGINNER"),
            authOf(instructorId, "INSTRUCTOR")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void list_returns_ok() {
        UUID adminId = UUID.randomUUID();
        when(listUseCase.list(adminId, true)).thenReturn(List.of(result()));

        ResponseEntity<VirtualCourseManagementListResponse> response = controller.list(authOf(adminId, "ADMIN"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().courses()).hasSize(1);
    }

    @Test
    void update_returns_200() {
        UUID ownerId = UUID.randomUUID();
        String courseId = UUID.randomUUID().toString();
        when(updateUseCase.update(eq(courseId), any(), eq(ownerId), eq(false))).thenReturn(result());

        ResponseEntity<VirtualCourseManagementResponse> response = controller.update(
            courseId, new UpdateVirtualCourseRequest(null, null, null, null, null, null, null),
            authOf(ownerId, "INSTRUCTOR")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void delete_returns_204() {
        UUID adminId = UUID.randomUUID();
        String courseId = UUID.randomUUID().toString();

        ResponseEntity<Void> response = controller.delete(courseId, authOf(adminId, "ADMIN"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void publish_returns_200() {
        UUID adminId = UUID.randomUUID();
        String courseId = UUID.randomUUID().toString();
        when(publishUseCase.publish(courseId, adminId, true)).thenReturn(result());

        ResponseEntity<VirtualCourseManagementResponse> response =
            controller.publish(courseId, authOf(adminId, "ADMIN"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void unpublish_returns_200() {
        UUID adminId = UUID.randomUUID();
        String courseId = UUID.randomUUID().toString();
        when(unpublishUseCase.unpublish(courseId, adminId, true)).thenReturn(result());

        ResponseEntity<VirtualCourseManagementResponse> response =
            controller.unpublish(courseId, authOf(adminId, "ADMIN"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void create_module_returns_201() {
        UUID ownerId = UUID.randomUUID();
        String courseId = UUID.randomUUID().toString();
        VirtualModuleManagementResult moduleResult =
            new VirtualModuleManagementResult(UUID.randomUUID().toString(), courseId, "M1", 0);
        when(createModuleUseCase.create(eq(courseId), any(), eq(ownerId), eq(false))).thenReturn(moduleResult);

        ResponseEntity<VirtualModuleManagementResponse> response = controller.createModule(
            courseId, new CreateVirtualModuleRequest("M1", null), authOf(ownerId, "INSTRUCTOR")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void reorder_modules_returns_ok_with_the_new_order() {
        UUID ownerId = UUID.randomUUID();
        String courseId = UUID.randomUUID().toString();
        VirtualModuleManagementResult moduleResult =
            new VirtualModuleManagementResult(UUID.randomUUID().toString(), courseId, "M1", 0);
        when(reorderUseCase.reorder(eq(courseId), any(), eq(ownerId), eq(false)))
            .thenReturn(List.of(moduleResult));

        ResponseEntity<VirtualModuleManagementListResponse> response = controller.reorderModules(
            courseId, new ReorderVirtualModulesRequest(List.of(moduleResult.moduleId())),
            authOf(ownerId, "INSTRUCTOR")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().modules()).hasSize(1);
    }

    private static VirtualCourseAdminDetailView adminView(CourseStatus status, String videoId) {
        return new VirtualCourseAdminDetailView(
            UUID.randomUUID().toString(),
            "Tango Básico",
            "Descripción larga",
            "https://cdn/tango.jpg",
            "tango",
            "BEGINNER",
            true,
            status,
            List.of(new VirtualModuleAdminDetail(
                UUID.randomUUID().toString(),
                "Introducción",
                1,
                List.of(new VirtualLessonAdminSummary(
                    UUID.randomUUID().toString(),
                    "Historia",
                    30,
                    true,
                    1,
                    videoId
                ))
            )),
            new VirtualCourseStats(1, 1, 30)
        );
    }

    @Test
    void get_returns_200_with_detail_for_a_DRAFT_course() {
        String courseId = UUID.randomUUID().toString();
        when(catalogPort.findByIdForAdmin(courseId))
            .thenReturn(Optional.of(adminView(CourseStatus.DRAFT, "vid-draft")));

        ResponseEntity<VirtualCourseAdminDetailResponse> response = controller.get(courseId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("DRAFT");
        assertThat(response.getBody().modules().get(0).lessons().get(0).videoId()).isEqualTo("vid-draft");
    }

    @Test
    void get_returns_200_with_detail_for_a_PUBLISHED_course() {
        String courseId = UUID.randomUUID().toString();
        when(catalogPort.findByIdForAdmin(courseId))
            .thenReturn(Optional.of(adminView(CourseStatus.PUBLISHED, "vid-published")));

        ResponseEntity<VirtualCourseAdminDetailResponse> response = controller.get(courseId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("PUBLISHED");
        assertThat(response.getBody().modules().get(0).lessons().get(0).videoId()).isEqualTo("vid-published");
    }

    @Test
    void get_returns_200_with_detail_for_an_ARCHIVED_course() {
        String courseId = UUID.randomUUID().toString();
        when(catalogPort.findByIdForAdmin(courseId))
            .thenReturn(Optional.of(adminView(CourseStatus.ARCHIVED, "vid-archived")));

        ResponseEntity<VirtualCourseAdminDetailResponse> response = controller.get(courseId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("ARCHIVED");
        assertThat(response.getBody().modules().get(0).lessons().get(0).videoId()).isEqualTo("vid-archived");
    }

    @Test
    void get_throws_CourseNotFoundException_when_courseId_does_not_exist() {
        // The @RestControllerAdvice maps CourseNotFoundException to a 404
        // ProblemDetail (see VirtualCourseExceptionHandler) — the unit
        // assertion here is the side: the controller raises the right
        // domain exception, NOT a generic ResponseStatusException or a
        // silently-empty 200.
        String courseId = UUID.randomUUID().toString();
        when(catalogPort.findByIdForAdmin(courseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get(courseId))
            .isInstanceOf(CourseNotFoundException.class);
    }
}
