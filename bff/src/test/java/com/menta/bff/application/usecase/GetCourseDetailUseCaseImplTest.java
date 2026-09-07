package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CourseDetail;
import com.menta.bff.application.port.out.VirtualApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetCourseDetailUseCaseImpl}.
 * <p>
 * Course detail is access-agnostic (design D2): this use case is a thin
 * pass-through with no branching, so it must merely forward the result or
 * let the port's exceptions propagate untranslated — the controller decides
 * how each exception renders.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetCourseDetailUseCase")
class GetCourseDetailUseCaseImplTest {

    private static final String COURSE_ID = "course-1";

    @Mock
    private VirtualApiClient virtualApiClient;

    private GetCourseDetailUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCourseDetailUseCaseImpl(virtualApiClient);
    }

    @Test
    @DisplayName("returns the course detail from the port on success")
    void shouldReturnCourseDetailOnSuccess() {
        CourseDetail courseDetail = sampleCourseDetail();
        when(virtualApiClient.getCourseDetail(COURSE_ID)).thenReturn(courseDetail);

        CourseDetail result = useCase.execute(COURSE_ID);

        assertThat(result).isEqualTo(courseDetail);
    }

    @Test
    @DisplayName("propagates NotFoundException untranslated")
    void shouldPropagateNotFoundException() {
        when(virtualApiClient.getCourseDetail(COURSE_ID))
                .thenThrow(new VirtualApiClient.NotFoundException("Course not found"));

        assertThatThrownBy(() -> useCase.execute(COURSE_ID))
                .isInstanceOf(VirtualApiClient.NotFoundException.class)
                .hasMessageContaining("Course not found");
    }

    @Test
    @DisplayName("propagates ServiceUnavailableException untranslated")
    void shouldPropagateServiceUnavailableException() {
        when(virtualApiClient.getCourseDetail(COURSE_ID))
                .thenThrow(new VirtualApiClient.ServiceUnavailableException("Virtual API unavailable"));

        assertThatThrownBy(() -> useCase.execute(COURSE_ID))
                .isInstanceOf(VirtualApiClient.ServiceUnavailableException.class)
                .hasMessageContaining("Virtual API unavailable");
    }

    @Test
    @DisplayName("throws NullPointerException when courseId is null")
    void shouldThrowNullPointerExceptionWhenCourseIdIsNull() {
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(NullPointerException.class);
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
