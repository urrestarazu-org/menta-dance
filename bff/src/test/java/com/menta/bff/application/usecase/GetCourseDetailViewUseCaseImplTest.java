package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CourseDetail;
import com.menta.bff.application.dto.CourseProgress;
import com.menta.bff.application.port.out.VirtualApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetCourseDetailViewUseCaseImpl}.
 * <p>
 * The load-bearing behavior under test is the broad collapse-to-{@link
 * CourseDetailView.Plain} rule (design D4): a missing token, a
 * zero-progress/zero-lesson course, every {@code RuntimeException} from the
 * progress call ({@code 403}/{@code 404}/unmapped {@code 401}/{@code 503}),
 * and an unresolvable resume {@code lessonId} all collapse to the identical
 * {@link CourseDetailView.Plain}, with zero distinct branches for the caller
 * to observe. Only the catalog call's own failures are exempt from this
 * guard and propagate untranslated, exactly as in #170's
 * {@code GetCourseDetailUseCaseImplTest}/{@code GetLessonViewUseCaseImplTest}.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetCourseDetailViewUseCase")
class GetCourseDetailViewUseCaseImplTest {

    private static final String COURSE_ID = "course-1";
    private static final String RESUMABLE_LESSON_ID = "lesson-2";
    private static final String STALE_LESSON_ID = "lesson-stale";
    private static final String TOKEN = "user-token-abc";

    @Mock
    private VirtualApiClient virtualApiClient;

    private GetCourseDetailViewUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCourseDetailViewUseCaseImpl(virtualApiClient);
    }

    @Test
    @DisplayName("null access token returns Plain and never calls getCourseProgress")
    void shouldReturnPlainAndSkipProgressCallWhenTokenIsNull() {
        CourseDetail course = sampleCourseDetail();
        when(virtualApiClient.getCourseDetail(COURSE_ID)).thenReturn(course);

        CourseDetailView result = useCase.execute(COURSE_ID, null);

        assertThat(result).isEqualTo(new CourseDetailView.Plain(course));
        verify(virtualApiClient, never()).getCourseProgress(any(), any());
    }

    @Test
    @DisplayName("present access token calls getCourseProgress with that exact token, never null")
    void shouldCallProgressWithCallersTokenWhenPresent() {
        CourseDetail course = sampleCourseDetail();
        when(virtualApiClient.getCourseDetail(COURSE_ID)).thenReturn(course);
        when(virtualApiClient.getCourseProgress(COURSE_ID, TOKEN)).thenReturn(zeroProgress());

        useCase.execute(COURSE_ID, TOKEN);

        verify(virtualApiClient).getCourseProgress(COURSE_ID, TOKEN);
    }

    @Test
    @DisplayName("resumable lesson present in the module tree returns Resumable with the resolved title and all fields")
    void shouldReturnResumableWhenLessonResolvesAgainstCourseDetail() {
        CourseDetail course = sampleCourseDetail();
        CourseProgress progress = new CourseProgress(
                COURSE_ID, 3, 10, 30,
                new CourseProgress.ResumeLesson(RESUMABLE_LESSON_ID, "module-1", 45, false));
        when(virtualApiClient.getCourseDetail(COURSE_ID)).thenReturn(course);
        when(virtualApiClient.getCourseProgress(COURSE_ID, TOKEN)).thenReturn(progress);

        CourseDetailView result = useCase.execute(COURSE_ID, TOKEN);

        assertThat(result).isInstanceOf(CourseDetailView.Resumable.class);
        CourseDetailView.Resumable resumable = (CourseDetailView.Resumable) result;
        assertThat(resumable.course()).isEqualTo(course);
        assertThat(resumable.resume())
                .isEqualTo(new CourseDetailView.Resume(RESUMABLE_LESSON_ID, "Primeros pasos", 30, 3, 10));
    }

    @Test
    @DisplayName("resume lessonId absent from the catalog projection returns Plain, no exception thrown")
    void shouldReturnPlainWhenResumeLessonIdDoesNotResolve() {
        CourseDetail course = sampleCourseDetail();
        CourseProgress progress = new CourseProgress(
                COURSE_ID, 1, 10, 10,
                new CourseProgress.ResumeLesson(STALE_LESSON_ID, "module-1", 10, false));
        when(virtualApiClient.getCourseDetail(COURSE_ID)).thenReturn(course);
        when(virtualApiClient.getCourseProgress(COURSE_ID, TOKEN)).thenReturn(progress);

        CourseDetailView result = useCase.execute(COURSE_ID, TOKEN);

        assertThat(result).isEqualTo(new CourseDetailView.Plain(course));
    }

    @Test
    @DisplayName("null resumeLesson (zero progress or zero-lesson course) returns Plain")
    void shouldReturnPlainWhenResumeLessonIsNull() {
        CourseDetail course = sampleCourseDetail();
        when(virtualApiClient.getCourseDetail(COURSE_ID)).thenReturn(course);
        when(virtualApiClient.getCourseProgress(COURSE_ID, TOKEN)).thenReturn(zeroProgress());

        CourseDetailView result = useCase.execute(COURSE_ID, TOKEN);

        assertThat(result).isEqualTo(new CourseDetailView.Plain(course));
    }

    @ParameterizedTest(name = "{0} from getCourseProgress collapses to Plain")
    @MethodSource("progressFailures")
    @DisplayName("every RuntimeException from getCourseProgress (403/404/401/503) collapses to Plain")
    void shouldReturnPlainWhenProgressCallFails(RuntimeException progressFailure) {
        CourseDetail course = sampleCourseDetail();
        when(virtualApiClient.getCourseDetail(COURSE_ID)).thenReturn(course);
        when(virtualApiClient.getCourseProgress(COURSE_ID, TOKEN)).thenThrow(progressFailure);

        CourseDetailView result = useCase.execute(COURSE_ID, TOKEN);

        assertThat(result).isEqualTo(new CourseDetailView.Plain(course));
    }

    private static Stream<RuntimeException> progressFailures() {
        return Stream.of(
                new VirtualApiClient.ForbiddenException("No current entitlement"),
                new VirtualApiClient.NotFoundException("Course not found"),
                new RuntimeException("Unexpected 401"),
                new VirtualApiClient.ServiceUnavailableException("Virtual API unavailable")
        );
    }

    @ParameterizedTest(name = "{0} from getCourseDetail propagates untranslated")
    @MethodSource("catalogFailures")
    @DisplayName("catalog failure is not caught by the progress guard and propagates untranslated")
    void shouldPropagateCatalogFailureWithoutCallingProgress(RuntimeException catalogFailure) {
        when(virtualApiClient.getCourseDetail(COURSE_ID)).thenThrow(catalogFailure);

        assertThatThrownBy(() -> useCase.execute(COURSE_ID, TOKEN))
                .isSameAs(catalogFailure);

        verify(virtualApiClient, never()).getCourseProgress(any(), any());
    }

    private static Stream<RuntimeException> catalogFailures() {
        return Stream.of(
                new VirtualApiClient.NotFoundException("Course not found"),
                new VirtualApiClient.ServiceUnavailableException("Virtual API unavailable")
        );
    }

    private CourseProgress zeroProgress() {
        return new CourseProgress(COURSE_ID, 0, 10, 0, null);
    }

    private CourseDetail sampleCourseDetail() {
        return new CourseDetail(
                COURSE_ID, "Ballet Básico", "Introducción al ballet clásico", "https://cdn/thumb.jpg",
                "ballet", "beginner", true,
                List.of(
                        new CourseDetail.Module("module-1", "Módulo 1", 1, List.of(
                                new CourseDetail.Lesson("lesson-1", "Postura básica", "05:00", true, 1),
                                new CourseDetail.Lesson(RESUMABLE_LESSON_ID, "Primeros pasos", "07:00", false, 2)
                        )),
                        new CourseDetail.Module("module-2", "Módulo 2", 2, List.of(
                                new CourseDetail.Lesson("lesson-3", "Salto básico", "04:00", true, 1)
                        ))
                ),
                new CourseDetail.Stats(2, 3, "16m")
        );
    }
}
