package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.CourseDetail;
import com.menta.bff.application.dto.LessonDetail;
import com.menta.bff.application.dto.LessonSummary;
import com.menta.bff.application.dto.Nav;
import com.menta.bff.application.usecase.GetLessonViewUseCase;
import com.menta.bff.application.usecase.LessonView;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Unit tests for {@link LessonViewController}, standalone MockMvc, no
 * security context — anonymous reachability is proven by the integration
 * tier (see {@code VirtualLearningSecurityIntegrationTest}), not here.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LessonViewController")
class LessonViewControllerTest {

    private static final String COURSE_ID = "course-1";
    private static final String LESSON_ID = "lesson-1";

    @Mock
    private GetLessonViewUseCase getLessonViewUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LessonViewController(getLessonViewUseCase)).build();
    }

    @Test
    @DisplayName("forwards the request's access token attribute to the use case and renders the player branch")
    void shouldForwardAccessTokenAndRenderPlayableBranch() throws Exception {
        LessonView.Playable playable = samplePlayable();
        when(getLessonViewUseCase.execute(COURSE_ID, LESSON_ID, "token-123")).thenReturn(playable);

        MvcResult result = mockMvc.perform(get("/courses/{courseId}/lessons/{lessonId}", COURSE_ID, LESSON_ID)
                        .requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, "token-123"))
                .andExpect(status().isOk())
                .andExpect(view().name("lesson"))
                .andExpect(model().attribute("playable", playable))
                .andReturn();

        verify(getLessonViewUseCase).execute(COURSE_ID, LESSON_ID, "token-123");
        assertThat(result.getModelAndView().getModel()).doesNotContainKey("sample");
    }

    @Test
    @DisplayName("forwards null when the access token attribute is absent and renders the sample-gate branch")
    void shouldForwardNullTokenAndRenderSampleBranch() throws Exception {
        LessonView.Sample sample = sampleSample();
        when(getLessonViewUseCase.execute(COURSE_ID, LESSON_ID, null)).thenReturn(sample);

        MvcResult result = mockMvc.perform(get("/courses/{courseId}/lessons/{lessonId}", COURSE_ID, LESSON_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("lesson"))
                .andExpect(model().attribute("sample", sample))
                .andReturn();

        verify(getLessonViewUseCase).execute(COURSE_ID, LESSON_ID, null);
        assertThat(result.getModelAndView().getModel()).doesNotContainKey("playable");
    }

    private LessonView.Playable samplePlayable() {
        LessonDetail lesson = new LessonDetail(
                LESSON_ID, "Postura básica", "Introducción a la postura", "05:00", 1, null,
                new LessonDetail.CourseRef(COURSE_ID, "Ballet Básico"),
                new LessonDetail.ModuleRef("module-1", "Módulo 1"),
                new Nav(null, new Nav.Ref("lesson-2", "Giro avanzado"))
        );
        return new LessonView.Playable(sampleCourseDetail(), lesson, "https://cdn/stream.m3u8", lesson.navigation());
    }

    private LessonView.Sample sampleSample() {
        LessonSummary summary = new LessonSummary("lesson-2", "Giro avanzado", "08:00", false, 2);
        Nav nav = new Nav(new Nav.Ref(LESSON_ID, "Postura básica"), null);
        return new LessonView.Sample(sampleCourseDetail(), summary, nav);
    }

    private CourseDetail sampleCourseDetail() {
        return new CourseDetail(
                COURSE_ID, "Ballet Básico", "Introducción al ballet clásico", "https://cdn/thumb.jpg",
                "ballet", "beginner", true,
                List.of(new CourseDetail.Module("module-1", "Módulo 1", 1, List.of(
                        new CourseDetail.Lesson(LESSON_ID, "Postura básica", "05:00", true, 1),
                        new CourseDetail.Lesson("lesson-2", "Giro avanzado", "08:00", false, 2)
                ))),
                new CourseDetail.Stats(1, 2, "13m")
        );
    }
}
