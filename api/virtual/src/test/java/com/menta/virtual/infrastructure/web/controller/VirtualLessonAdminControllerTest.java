package com.menta.virtual.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import com.menta.virtual.application.port.in.DeleteVirtualLessonUseCase;
import com.menta.virtual.application.port.in.UpdateVirtualLessonUseCase;
import com.menta.virtual.domain.exception.CourseNotOwnedException;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.infrastructure.web.dto.UpdateVirtualLessonRequest;
import com.menta.virtual.infrastructure.web.dto.VirtualLessonManagementResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Lesson management adapter. The {@code DELETE} cases run through MockMvc with the real {@link
 * VirtualCourseExceptionHandler}, so status, problem body and empty {@code 204} are the wire ones. The role
 * gate (anonymous {@code 401}, {@code STUDENT}/{@code RECEPTIONIST} {@code 403}) belongs to the security chain
 * of {@code :api:auth} and is pinned by {@code VirtualLessonDeletionIntegrationTest} in {@code :api:app}.
 */
class VirtualLessonAdminControllerTest {

    private static final String URL = "/api/v1/admin/virtual/lessons/";

    private final UpdateVirtualLessonUseCase updateUseCase = mock(UpdateVirtualLessonUseCase.class);
    private final DeleteVirtualLessonUseCase deleteUseCase = mock(DeleteVirtualLessonUseCase.class);
    private final VirtualLessonAdminController controller =
        new VirtualLessonAdminController(updateUseCase, deleteUseCase);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new VirtualCourseExceptionHandler())
        .build();

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

    @Test
    void delete_by_an_instructor_answers_204_without_a_body_and_is_not_treated_as_admin() throws Exception {
        UUID instructorId = UUID.randomUUID();
        String lessonId = UUID.randomUUID().toString();

        mockMvc.perform(delete(URL + lessonId).principal(authOf(instructorId, "INSTRUCTOR")))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(deleteUseCase).delete(lessonId, instructorId, false);
    }

    @Test
    void delete_by_an_admin_is_passed_as_admin() throws Exception {
        UUID adminId = UUID.randomUUID();
        String lessonId = UUID.randomUUID().toString();

        mockMvc.perform(delete(URL + lessonId).principal(authOf(adminId, "ADMIN")))
            .andExpect(status().isNoContent());

        verify(deleteUseCase).delete(lessonId, adminId, true);
    }

    @Test
    void delete_of_an_unknown_lesson_answers_404_lesson_not_found() throws Exception {
        UUID adminId = UUID.randomUUID();
        String lessonId = UUID.randomUUID().toString();
        doThrow(new LessonNotFoundException()).when(deleteUseCase).delete(lessonId, adminId, true);

        mockMvc.perform(delete(URL + lessonId).principal(authOf(adminId, "ADMIN")))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("LESSON_NOT_FOUND"))
            .andExpect(jsonPath("$.detail").value("Lesson not found."));
    }

    @Test
    void delete_of_a_lesson_of_another_instructors_course_answers_403_course_not_owned() throws Exception {
        UUID instructorId = UUID.randomUUID();
        String lessonId = UUID.randomUUID().toString();
        doThrow(new CourseNotOwnedException()).when(deleteUseCase).delete(lessonId, instructorId, false);

        mockMvc.perform(delete(URL + lessonId).principal(authOf(instructorId, "INSTRUCTOR")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("COURSE_NOT_OWNED"));
    }

    @Test
    void delete_with_a_malformed_id_answers_400_invalid_request() throws Exception {
        UUID adminId = UUID.randomUUID();
        // The use case parses the id first (LessonId.of), exactly as the PUT does.
        doThrow(new IllegalArgumentException("bad id")).when(deleteUseCase).delete("not-a-uuid", adminId, true);

        mockMvc.perform(delete(URL + "not-a-uuid").principal(authOf(adminId, "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
