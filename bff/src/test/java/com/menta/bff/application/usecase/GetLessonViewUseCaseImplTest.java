package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CourseDetail;
import com.menta.bff.application.dto.LessonDetail;
import com.menta.bff.application.dto.LessonStream;
import com.menta.bff.application.dto.LessonSummary;
import com.menta.bff.application.dto.Nav;
import com.menta.bff.application.port.out.VirtualApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetLessonViewUseCaseImpl}.
 * <p>
 * The load-bearing behavior under test: a {@code 200} from the lesson
 * endpoint means access was granted, not that a playable URL was supplied
 * with it — {@code getStream} MUST be called for every granted lesson, free
 * ones included. Two earlier authoring passes (spec and design) independently
 * inferred from {@code videoId: null} that free lessons skip the stream
 * call; {@link #shouldReturnPlayableForFreeGrantedLessonCallingStreamWithNullToken()}
 * is the case that would have caught that bug.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetLessonViewUseCase")
class GetLessonViewUseCaseImplTest {

    private static final String COURSE_ID = "course-1";
    private static final String FREE_LESSON_ID = "lesson-1";
    private static final String PREMIUM_LESSON_ID = "lesson-2";
    private static final String THIRD_LESSON_ID = "lesson-3";

    @Mock
    private VirtualApiClient virtualApiClient;

    private GetLessonViewUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetLessonViewUseCaseImpl(virtualApiClient);
    }

    @ParameterizedTest(name = "{0} short-circuits before any lesson/stream call")
    @MethodSource("catalogFailures")
    @DisplayName("catalog failure short-circuits before any lesson/stream call")
    void shouldPropagateCatalogFailureWithoutCallingLessonOrStream(RuntimeException catalogFailure) {
        when(virtualApiClient.getCourseDetail(COURSE_ID)).thenThrow(catalogFailure);

        assertThatThrownBy(() -> useCase.execute(COURSE_ID, PREMIUM_LESSON_ID, null))
                .isSameAs(catalogFailure);

        verify(virtualApiClient, never()).getLesson(any(), any());
        verify(virtualApiClient, never()).getStream(any(), any());
    }

    private static Stream<RuntimeException> catalogFailures() {
        return Stream.of(
                new VirtualApiClient.NotFoundException("Course not found"),
                new VirtualApiClient.ServiceUnavailableException("Virtual API unavailable")
        );
    }

    @Test
    @DisplayName("lesson 403 returns a Sample built from course detail, nav from course order, zero getStream calls")
    void shouldReturnSampleWhenLessonDenied() {
        CourseDetail course = sampleCourseDetail();
        when(virtualApiClient.getCourseDetail(COURSE_ID)).thenReturn(course);
        when(virtualApiClient.getLesson(PREMIUM_LESSON_ID, null))
                .thenThrow(new VirtualApiClient.ForbiddenException("Subscription required"));

        LessonView result = useCase.execute(COURSE_ID, PREMIUM_LESSON_ID, null);

        assertThat(result).isInstanceOf(LessonView.Sample.class);
        LessonView.Sample sample = (LessonView.Sample) result;
        assertThat(sample.course()).isEqualTo(course);
        assertThat(sample.lesson())
                .isEqualTo(new LessonSummary(PREMIUM_LESSON_ID, "Primeros pasos", "07:00", false, 2));
        assertThat(sample.nav().previousLesson()).isEqualTo(new Nav.Ref(FREE_LESSON_ID, "Postura básica"));
        assertThat(sample.nav().nextLesson()).isEqualTo(new Nav.Ref(THIRD_LESSON_ID, "Salto básico"));

        verify(virtualApiClient, never()).getStream(any(), any());
    }

    @Test
    @DisplayName("free lesson granted (200, videoId null) returns Playable and still calls getStream with a null token, non-empty streamUrl")
    void shouldReturnPlayableForFreeGrantedLessonCallingStreamWithNullToken() {
        CourseDetail course = sampleCourseDetail();
        LessonDetail freeLesson = sampleLessonDetail(FREE_LESSON_ID, null, sampleNavigation());
        LessonStream stream = new LessonStream("https://cdn/stream/free.m3u8", Instant.parse("2026-09-07T12:00:00Z"));
        when(virtualApiClient.getCourseDetail(COURSE_ID)).thenReturn(course);
        when(virtualApiClient.getLesson(FREE_LESSON_ID, null)).thenReturn(freeLesson);
        when(virtualApiClient.getStream(FREE_LESSON_ID, null)).thenReturn(stream);

        LessonView result = useCase.execute(COURSE_ID, FREE_LESSON_ID, null);

        assertThat(result).isInstanceOf(LessonView.Playable.class);
        LessonView.Playable playable = (LessonView.Playable) result;
        assertThat(playable.streamUrl()).isEqualTo(stream.url()).isNotBlank();
        verify(virtualApiClient).getStream(FREE_LESSON_ID, null);
    }

    @Test
    @DisplayName("entitled premium lesson (200, videoId present) returns Playable and calls getStream with the caller's token")
    void shouldReturnPlayableForEntitledLessonCallingStreamWithCallerToken() {
        String token = "user-token-abc";
        CourseDetail course = sampleCourseDetail();
        LessonDetail premiumLesson = sampleLessonDetail(PREMIUM_LESSON_ID, "bunny-video-id", sampleNavigation());
        LessonStream stream = new LessonStream("https://cdn/stream/premium.m3u8", Instant.parse("2026-09-07T12:00:00Z"));
        when(virtualApiClient.getCourseDetail(COURSE_ID)).thenReturn(course);
        when(virtualApiClient.getLesson(PREMIUM_LESSON_ID, token)).thenReturn(premiumLesson);
        when(virtualApiClient.getStream(PREMIUM_LESSON_ID, token)).thenReturn(stream);

        LessonView result = useCase.execute(COURSE_ID, PREMIUM_LESSON_ID, token);

        assertThat(result).isInstanceOf(LessonView.Playable.class);
        LessonView.Playable playable = (LessonView.Playable) result;
        assertThat(playable.streamUrl()).isEqualTo(stream.url());
        verify(virtualApiClient).getStream(PREMIUM_LESSON_ID, token);
    }

    @Test
    @DisplayName("granted lesson (200) renders navigation from the lesson endpoint's own block, not course-detail order")
    void shouldUseNavigationFromLessonEndpointWhenGranted() {
        CourseDetail course = sampleCourseDetail();
        Nav upstreamNav = new Nav(
                new Nav.Ref("other-lesson-a", "Otro anterior"),
                new Nav.Ref("other-lesson-b", "Otro siguiente")
        );
        LessonDetail lesson = sampleLessonDetail(PREMIUM_LESSON_ID, "bunny-video-id", upstreamNav);
        LessonStream stream = new LessonStream("https://cdn/stream/premium.m3u8", Instant.parse("2026-09-07T12:00:00Z"));
        when(virtualApiClient.getCourseDetail(COURSE_ID)).thenReturn(course);
        when(virtualApiClient.getLesson(PREMIUM_LESSON_ID, null)).thenReturn(lesson);
        when(virtualApiClient.getStream(PREMIUM_LESSON_ID, null)).thenReturn(stream);

        LessonView result = useCase.execute(COURSE_ID, PREMIUM_LESSON_ID, null);

        LessonView.Playable playable = (LessonView.Playable) result;
        assertThat(playable.nav()).isEqualTo(upstreamNav);
    }

    @Test
    @DisplayName("getStream 503 after a granted lesson propagates the exception, no partial player")
    void shouldPropagateExceptionWhenStreamFailsAfterGrantedLesson() {
        CourseDetail course = sampleCourseDetail();
        LessonDetail freeLesson = sampleLessonDetail(FREE_LESSON_ID, null, sampleNavigation());
        when(virtualApiClient.getCourseDetail(COURSE_ID)).thenReturn(course);
        when(virtualApiClient.getLesson(FREE_LESSON_ID, null)).thenReturn(freeLesson);
        when(virtualApiClient.getStream(FREE_LESSON_ID, null))
                .thenThrow(new VirtualApiClient.ServiceUnavailableException("Virtual API unavailable"));

        assertThatThrownBy(() -> useCase.execute(COURSE_ID, FREE_LESSON_ID, null))
                .isInstanceOf(VirtualApiClient.ServiceUnavailableException.class)
                .hasMessageContaining("Virtual API unavailable");
    }

    private CourseDetail sampleCourseDetail() {
        return new CourseDetail(
                COURSE_ID, "Ballet Básico", "Introducción al ballet clásico", "https://cdn/thumb.jpg",
                "ballet", "beginner", true,
                List.of(
                        new CourseDetail.Module("module-1", "Módulo 1", 1, List.of(
                                new CourseDetail.Lesson(FREE_LESSON_ID, "Postura básica", "05:00", true, 1),
                                new CourseDetail.Lesson(PREMIUM_LESSON_ID, "Primeros pasos", "07:00", false, 2)
                        )),
                        new CourseDetail.Module("module-2", "Módulo 2", 2, List.of(
                                new CourseDetail.Lesson(THIRD_LESSON_ID, "Salto básico", "04:00", true, 1)
                        ))
                ),
                new CourseDetail.Stats(2, 3, "16m")
        );
    }

    private static LessonDetail sampleLessonDetail(String lessonId, String videoId, Nav navigation) {
        return new LessonDetail(
                lessonId, "Título de lección", "Descripción de la lección", "05:00", 1, videoId,
                new LessonDetail.CourseRef(COURSE_ID, "Ballet Básico"),
                new LessonDetail.ModuleRef("module-1", "Módulo 1"),
                navigation
        );
    }

    private static Nav sampleNavigation() {
        return new Nav(null, new Nav.Ref(PREMIUM_LESSON_ID, "Primeros pasos"));
    }
}
