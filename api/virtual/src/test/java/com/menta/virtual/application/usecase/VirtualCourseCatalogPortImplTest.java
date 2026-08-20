package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.VirtualCourseSummary;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VirtualCourseCatalogPortImplTest {

    private final VirtualCourseRepository repository = mock(VirtualCourseRepository.class);
    private final VirtualCourseCatalogPortImpl port = new VirtualCourseCatalogPortImpl(repository);

    private static VirtualCourse course(CourseId id) {
        return new VirtualCourse(
            id, "Tango Básico", "Aprendé los pasos fundamentales", "Descripción larga", UUID.randomUUID(),
            "https://cdn/tango.jpg", CourseCategory.of("tango"), CourseLevel.BEGINNER, true,
            CourseStatus.PUBLISHED, 5, 20, 150
        );
    }

    @Test
    void maps_the_repository_result_into_plain_summaries() {
        CourseId id = CourseId.generate();
        when(repository.findPublished(isNull(), eq(10))).thenReturn(List.of(course(id)));

        List<VirtualCourseSummary> result = port.listPublished(null, 10);

        assertThat(result).containsExactly(new VirtualCourseSummary(
            id.toString(), "Tango Básico", "Aprendé los pasos fundamentales",
            "https://cdn/tango.jpg", "tango", "BEGINNER", true, 5, 20, 150
        ));
    }

    @Test
    void a_null_cursor_means_first_page() {
        when(repository.findPublished(any(), eq(5))).thenReturn(List.of());

        port.listPublished(null, 5);

        verify(repository).findPublished(isNull(), eq(5));
    }

    @Test
    void a_present_cursor_is_parsed_and_forwarded() {
        CourseId cursor = CourseId.generate();
        when(repository.findPublished(any(), eq(5))).thenReturn(List.of());

        port.listPublished(cursor.toString(), 5);

        verify(repository).findPublished(eq(cursor), eq(5));
    }

    @Test
    void no_published_courses_returns_an_empty_list_not_an_exception() {
        when(repository.findPublished(isNull(), eq(10))).thenReturn(List.of());

        assertThat(port.listPublished(null, 10)).isEmpty();
    }

    @Test
    void find_published_by_id_maps_the_repository_result() {
        CourseId id = CourseId.generate();
        when(repository.findPublishedById(eq(id))).thenReturn(Optional.of(course(id)));

        Optional<VirtualCourseSummary> result = port.findPublishedById(id.toString());

        assertThat(result).contains(new VirtualCourseSummary(
            id.toString(), "Tango Básico", "Aprendé los pasos fundamentales",
            "https://cdn/tango.jpg", "tango", "BEGINNER", true, 5, 20, 150
        ));
    }

    @Test
    void find_published_by_id_returns_empty_when_the_repository_finds_nothing() {
        CourseId id = CourseId.generate();
        when(repository.findPublishedById(eq(id))).thenReturn(Optional.empty());

        assertThat(port.findPublishedById(id.toString())).isEmpty();
    }
}
