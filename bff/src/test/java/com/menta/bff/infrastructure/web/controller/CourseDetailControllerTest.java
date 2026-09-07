package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.CourseDetail;
import com.menta.bff.application.port.out.VirtualApiClient;
import com.menta.bff.application.usecase.CourseDetailView;
import com.menta.bff.application.usecase.GetCourseDetailViewUseCase;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private static final String ACCESS_TOKEN = "access-token-abc";

    @Mock
    private GetCourseDetailViewUseCase getCourseDetailViewUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CourseDetailController(getCourseDetailViewUseCase)).build();
    }

    @Test
    @DisplayName("anonymous request: no access-token attribute forwards null and renders with no resume attribute")
    void shouldForwardNullTokenAndRenderPlainWhenNoAccessTokenAttribute() throws Exception {
        CourseDetail courseDetail = sampleCourseDetail();
        when(getCourseDetailViewUseCase.execute(eq(COURSE_ID), isNull()))
                .thenReturn(new CourseDetailView.Plain(courseDetail));

        mockMvc.perform(get("/courses/{courseId}", COURSE_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("course-detail"))
                .andExpect(model().attribute("course", courseDetail))
                .andExpect(model().attributeDoesNotExist("resume"));
    }

    @Test
    @DisplayName("authenticated request with Resumable view: renders with the resume attribute populated")
    void shouldRenderResumeAttributeWhenViewIsResumable() throws Exception {
        CourseDetail courseDetail = sampleCourseDetail();
        CourseDetailView.Resume resume = new CourseDetailView.Resume("lesson-2", "Primeros pasos", 50, 1, 2);
        when(getCourseDetailViewUseCase.execute(eq(COURSE_ID), eq(ACCESS_TOKEN)))
                .thenReturn(new CourseDetailView.Resumable(courseDetail, resume));

        mockMvc.perform(get("/courses/{courseId}", COURSE_ID)
                        .requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("course-detail"))
                .andExpect(model().attribute("course", courseDetail))
                .andExpect(model().attribute("resume", resume));
    }

    @Test
    @DisplayName("authenticated request with Plain view: renders with no resume attribute")
    void shouldRenderNoResumeAttributeWhenViewIsPlain() throws Exception {
        CourseDetail courseDetail = sampleCourseDetail();
        when(getCourseDetailViewUseCase.execute(eq(COURSE_ID), eq(ACCESS_TOKEN)))
                .thenReturn(new CourseDetailView.Plain(courseDetail));

        mockMvc.perform(get("/courses/{courseId}", COURSE_ID)
                        .requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("course-detail"))
                .andExpect(model().attribute("course", courseDetail))
                .andExpect(model().attributeDoesNotExist("resume"));
    }

    @Test
    @DisplayName("propagates NotFoundException, which resolves to HTTP 404 via @ResponseStatus")
    void shouldResolveNotFoundExceptionTo404() throws Exception {
        when(getCourseDetailViewUseCase.execute(eq(COURSE_ID), isNull()))
                .thenThrow(new VirtualApiClient.NotFoundException("Course not found"));

        mockMvc.perform(get("/courses/{courseId}", COURSE_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("propagates ServiceUnavailableException, which resolves to HTTP 503 via @ResponseStatus")
    void shouldResolveServiceUnavailableExceptionTo503() throws Exception {
        when(getCourseDetailViewUseCase.execute(eq(COURSE_ID), isNull()))
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
