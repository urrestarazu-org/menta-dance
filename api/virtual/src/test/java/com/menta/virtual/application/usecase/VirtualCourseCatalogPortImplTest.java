package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.VirtualCourseSummary;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VirtualCourseCatalogPortImplTest {

    private final VirtualCourseRepository repository = mock(VirtualCourseRepository.class);
    private final VirtualModuleRepository moduleRepository = mock(VirtualModuleRepository.class);
    private final VirtualLessonRepository lessonRepository = mock(VirtualLessonRepository.class);
    private final VirtualCourseCatalogPortImpl port =
        new VirtualCourseCatalogPortImpl(repository, moduleRepository, lessonRepository);

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

    @Test
    void find_published_by_ids_skips_malformed_blank_and_null_ids_and_queries_only_the_valid_one() {
        CourseId id = CourseId.generate();
        when(repository.findPublishedByIds(any())).thenReturn(List.of(course(id)));

        Map<String, VirtualCourseSummary> result =
            port.findPublishedByIds(Arrays.asList("course-1", " ", null, id.toString()));

        assertThat(queriedIds()).containsExactly(id);
        assertThat(result).containsOnlyKeys(id.toString());
    }

    @Test
    void find_published_by_ids_with_no_valid_id_returns_an_empty_map_without_querying() {
        Map<String, VirtualCourseSummary> result =
            port.findPublishedByIds(Arrays.asList("course-1", "", null));

        assertThat(result).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void find_published_by_ids_with_an_empty_collection_returns_an_empty_map_without_querying() {
        assertThat(port.findPublishedByIds(List.of())).isEmpty();

        verifyNoInteractions(repository);
    }

    @Test
    void find_published_by_ids_keys_the_result_by_the_input_string_not_the_canonical_id() {
        CourseId id = CourseId.generate();
        String upperCaseInput = id.toString().toUpperCase();
        when(repository.findPublishedByIds(any())).thenReturn(List.of(course(id)));

        Map<String, VirtualCourseSummary> result = port.findPublishedByIds(List.of(upperCaseInput));

        assertThat(result).containsOnlyKeys(upperCaseInput);
        assertThat(result.get(upperCaseInput).courseId()).isEqualTo(id.toString());
    }

    @Test
    void find_published_by_ids_looks_a_duplicated_id_up_once_and_answers_every_input_form() {
        CourseId id = CourseId.generate();
        String lowerCaseInput = id.toString();
        String upperCaseInput = id.toString().toUpperCase();
        when(repository.findPublishedByIds(any())).thenReturn(List.of(course(id)));

        Map<String, VirtualCourseSummary> result = port.findPublishedByIds(
            List.of(lowerCaseInput, lowerCaseInput, upperCaseInput)
        );

        assertThat(queriedIds()).containsExactly(id);
        assertThat(result).containsOnlyKeys(lowerCaseInput, upperCaseInput);
    }

    @Test
    void find_published_by_ids_omits_the_ids_the_repository_did_not_answer() {
        CourseId published = CourseId.generate();
        CourseId notVisible = CourseId.generate();
        when(repository.findPublishedByIds(any())).thenReturn(List.of(course(published)));

        Map<String, VirtualCourseSummary> result =
            port.findPublishedByIds(List.of(published.toString(), notVisible.toString()));

        assertThat(queriedIds()).containsExactlyInAnyOrder(published, notVisible);
        assertThat(result).containsOnlyKeys(published.toString());
    }

    @Test
    void find_published_by_ids_rejects_a_null_collection() {
        assertThatThrownBy(() -> port.findPublishedByIds(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void find_published_by_ids_maps_a_hit_to_the_same_summary_as_find_published_by_id() {
        CourseId id = CourseId.generate();
        when(repository.findPublishedById(eq(id))).thenReturn(Optional.of(course(id)));
        when(repository.findPublishedByIds(any())).thenReturn(List.of(course(id)));

        VirtualCourseSummary single = port.findPublishedById(id.toString()).orElseThrow();
        VirtualCourseSummary batch =
            port.findPublishedByIds(List.of(id.toString())).get(id.toString());

        assertThat(batch).isEqualTo(single);
    }

    @SuppressWarnings("unchecked")
    private Collection<CourseId> queriedIds() {
        ArgumentCaptor<Collection<CourseId>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(repository).findPublishedByIds(captor.capture());
        return captor.getValue();
    }
}
