package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.CourseDetail;
import com.menta.bff.application.port.out.VirtualApiClient;
import com.menta.bff.application.usecase.GetCourseDetailUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Unit tests for {@link CourseDetailController}, standalone MockMvc, no
 * security context — anonymous reachability is proven by the integration
 * tier (see {@code VirtualLearningSecurityIntegrationTest}), not here.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CourseDetailController")
class CourseDetailControllerTest {

    private static final String COURSE_ID = "course-1";

    @Mock
    private GetCourseDetailUseCase getCourseDetailUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CourseDetailController(getCourseDetailUseCase)).build();
    }

    @Test
    @DisplayName("renders course-detail with the module/lesson tree and free/premium markers")
    void shouldRenderCourseDetailWithModuleLessonTree() throws Exception {
        CourseDetail courseDetail = sampleCourseDetail();
        when(getCourseDetailUseCase.execute(COURSE_ID)).thenReturn(courseDetail);

        mockMvc.perform(get("/courses/{courseId}", COURSE_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("course-detail"))
                .andExpect(model().attribute("course", courseDetail));
    }

    @Test
    @DisplayName("propagates NotFoundException, which resolves to HTTP 404 via @ResponseStatus")
    void shouldResolveNotFoundExceptionTo404() throws Exception {
        when(getCourseDetailUseCase.execute(COURSE_ID))
                .thenThrow(new VirtualApiClient.NotFoundException("Course not found"));

        mockMvc.perform(get("/courses/{courseId}", COURSE_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("propagates ServiceUnavailableException, which resolves to HTTP 503 via @ResponseStatus")
    void shouldResolveServiceUnavailableExceptionTo503() throws Exception {
        when(getCourseDetailUseCase.execute(COURSE_ID))
                .thenThrow(new VirtualApiClient.ServiceUnavailableException("Virtual API unavailable"));

        mockMvc.perform(get("/courses/{courseId}", COURSE_ID))
                .andExpect(status().isServiceUnavailable());
    }

    private CourseDetail sampleCourseDetail() {
        return new CourseDetail(
                COURSE_ID, "Ballet Básico", "Introducción al ballet clásico", "https://cdn/thumb.jpg",
                "ballet", "beginner", true,
                List.of(new CourseDetail.Module("module-1", "Módulo 1", 1, List.of(
                        new CourseDetail.Lesson("lesson-1", "Postura básica", "05:00", true, 1),
                        new CourseDetail.Lesson("lesson-2", "Primeros pasos", "07:00", false, 2)
                ))),
                new CourseDetail.Stats(1, 2, "12m")
        );
    }
}
