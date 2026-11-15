package com.menta.virtual.infrastructure.web.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.virtual.application.dto.InProgressCourseView;
import com.menta.virtual.application.port.in.ListInProgressCoursesUseCase;
import com.menta.virtual.domain.exception.InvalidInProgressCoursesLimitException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Web-slice test (#765) of the in-progress listing: request binding, the scoped advice chain of
 * {@link VirtualStudentEndpoint} and the wire shape. The {@code 401} without a token belongs to
 * the security chain of {@code :api:auth} and is pinned by {@code SecurityConfigTest} and the
 * MySQL integration test of {@code :api:app}.
 */
class VirtualStudentCoursesControllerTest {

    private static final String URL = "/api/v1/virtual/courses/me/in-progress";

    private final ListInProgressCoursesUseCase useCase = mock(ListInProgressCoursesUseCase.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new VirtualStudentCoursesController(useCase))
        .setControllerAdvice(new VirtualStudentProgressExceptionHandler())
        .build();
    private final UUID userId = UUID.randomUUID();

    private Authentication student() {
        return new UsernamePasswordAuthenticationToken(
            userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
    }

    private static InProgressCourseView course(String courseId) {
        return new InProgressCourseView(
            courseId, "Tango Básico", 1, 4, 25,
            new InProgressCourseView.ResumeLesson("lesson-1", "module-1", "Postura", 95)
        );
    }

    @Test
    void lists_the_callers_courses_with_the_default_limit_and_the_documented_shape() throws Exception {
        when(useCase.list(userId, ListInProgressCoursesUseCase.DEFAULT_LIMIT)).thenReturn(List.of(course("c-1")));

        mockMvc.perform(get(URL).principal(student()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].length()").value(6))
            .andExpect(jsonPath("$[0].courseId").value("c-1"))
            .andExpect(jsonPath("$[0].courseTitle").value("Tango Básico"))
            .andExpect(jsonPath("$[0].completedLessons").value(1))
            .andExpect(jsonPath("$[0].totalLessons").value(4))
            .andExpect(jsonPath("$[0].percentage").value(25))
            .andExpect(jsonPath("$[0].resumeLesson.length()").value(4))
            .andExpect(jsonPath("$[0].resumeLesson.lessonId").value("lesson-1"))
            .andExpect(jsonPath("$[0].resumeLesson.moduleId").value("module-1"))
            .andExpect(jsonPath("$[0].resumeLesson.lessonTitle").value("Postura"))
            .andExpect(jsonPath("$[0].resumeLesson.positionSeconds").value(95));
    }

    @Test
    void passes_the_requested_limit_to_the_use_case() throws Exception {
        when(useCase.list(userId, 3)).thenReturn(List.of());

        mockMvc.perform(get(URL).param("limit", "3").principal(student()))
            .andExpect(status().isOk());

        verify(useCase).list(eq(userId), eq(3));
    }

    @Test
    void no_course_in_progress_is_an_empty_array_not_an_error() throws Exception {
        when(useCase.list(userId, ListInProgressCoursesUseCase.DEFAULT_LIMIT)).thenReturn(List.of());

        mockMvc.perform(get(URL).principal(student()))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    void a_non_integer_limit_is_a_400_problem_with_a_stable_code() throws Exception {
        mockMvc.perform(get(URL).param("limit", "diez").principal(student()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(useCase);
    }

    @Test
    void an_out_of_range_limit_rejected_by_the_use_case_is_a_400_problem_with_the_same_code() throws Exception {
        when(useCase.list(eq(userId), anyInt())).thenThrow(new InvalidInProgressCoursesLimitException(51, 50));

        mockMvc.perform(get(URL).param("limit", "51").principal(student()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
