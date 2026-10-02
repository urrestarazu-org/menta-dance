package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.PhysicalCourseSummary;
import com.menta.physical.application.dto.PhysicalSessionAvailability;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhysicalCourseAvailabilityPortImplTest {

    @Mock private PhysicalCourseRepository courseRepository;
    @Mock private PhysicalSessionRepository sessionRepository;

    private PhysicalCourseAvailabilityPortImpl port;

    @BeforeEach
    void setUp() {
        port = new PhysicalCourseAvailabilityPortImpl(courseRepository, sessionRepository);
    }

    @Test
    void lists_courses_mapped_to_the_cross_module_summary_shape() {
        CourseId id = CourseId.generate();
        PhysicalCourse course = new PhysicalCourse(
            id, "Salsa inicial", "desc", UUID.randomUUID(), "María García", DayOfWeek.TUESDAY,
            LocalTime.of(19, 0), 60, PhysicalCourseLevel.BEGINNER, 20, CourseStatus.ACTIVE
        );
        when(courseRepository.findActive(null, 10)).thenReturn(List.of(course));

        List<PhysicalCourseSummary> result = port.listCourses(null, 10);

        assertThat(result).containsExactly(new PhysicalCourseSummary(
            id.toString(), "Salsa inicial", "María García", "TUESDAY", "19:00", "BEGINNER", 20
        ));
    }

    @Test
    void list_courses_resolves_a_non_null_cursor_before_delegating() {
        CourseId cursor = CourseId.generate();
        when(courseRepository.findActive(any(), eq(5))).thenReturn(List.of());

        port.listCourses(cursor.toString(), 5);

        verify(courseRepository).findActive(eq(cursor), eq(5));
    }

    @Test
    void empty_course_repository_yields_an_empty_list_not_an_exception() {
        when(courseRepository.findActive(null, 10)).thenReturn(List.of());

        assertThat(port.listCourses(null, 10)).isEmpty();
    }

    @Test
    void find_active_by_id_maps_the_repository_result() {
        CourseId id = CourseId.generate();
        PhysicalCourse course = new PhysicalCourse(
            id, "Salsa inicial", "desc", UUID.randomUUID(), "María García", DayOfWeek.TUESDAY,
            LocalTime.of(19, 0), 60, PhysicalCourseLevel.BEGINNER, 20, CourseStatus.ACTIVE
        );
        when(courseRepository.findActiveById(id)).thenReturn(Optional.of(course));

        Optional<PhysicalCourseSummary> result = port.findActiveById(id.toString());

        assertThat(result).contains(new PhysicalCourseSummary(
            id.toString(), "Salsa inicial", "María García", "TUESDAY", "19:00", "BEGINNER", 20
        ));
    }

    @Test
    void find_active_by_id_returns_empty_when_the_repository_finds_nothing() {
        CourseId id = CourseId.generate();
        when(courseRepository.findActiveById(id)).thenReturn(Optional.empty());

        assertThat(port.findActiveById(id.toString())).isEmpty();
    }

    @Test
    void lists_sessions_with_availability_mapped_to_the_cross_module_shape() {
        CourseId courseId = CourseId.generate();
        SessionId sessionId = SessionId.generate();
        Instant scheduledAt = Instant.parse("2026-08-25T22:00:00Z");
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-01T00:00:00Z");
        PhysicalSession session = new PhysicalSession(
            sessionId, courseId, scheduledAt, 20, 5, 3, com.menta.physical.domain.model.SessionStatus.SCHEDULED, null
        );
        when(sessionRepository.findScheduled(courseId, from, to)).thenReturn(List.of(session));

        List<PhysicalSessionAvailability> result = port.listSessions(courseId.toString(), from, to);

        assertThat(result).containsExactly(new PhysicalSessionAvailability(
            sessionId.toString(), courseId.toString(), scheduledAt.toString(), 20, 5, 3, 12
        ));
    }

    @Test
    void empty_session_repository_yields_an_empty_list_not_an_exception() {
        CourseId courseId = CourseId.generate();
        Instant from = Instant.parse("2026-08-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-01T00:00:00Z");
        when(sessionRepository.findScheduled(courseId, from, to)).thenReturn(List.of());

        assertThat(port.listSessions(courseId.toString(), from, to)).isEmpty();
    }

    @Test
    void find_active_by_ids_skips_malformed_blank_and_null_ids_and_queries_only_the_valid_one() {
        CourseId id = CourseId.generate();
        when(courseRepository.findActiveByIds(any())).thenReturn(List.of(activeCourse(id)));

        Map<String, PhysicalCourseSummary> result =
            port.findActiveByIds(Arrays.asList("course-1", " ", null, id.toString()));

        assertThat(queriedIds()).containsExactly(id);
        assertThat(result).containsOnlyKeys(id.toString());
    }

    @Test
    void find_active_by_ids_with_no_valid_id_returns_an_empty_map_without_querying() {
        Map<String, PhysicalCourseSummary> result =
            port.findActiveByIds(Arrays.asList("course-1", "", null));

        assertThat(result).isEmpty();
        verifyNoInteractions(courseRepository);
    }

    @Test
    void find_active_by_ids_with_an_empty_collection_returns_an_empty_map_without_querying() {
        assertThat(port.findActiveByIds(List.of())).isEmpty();

        verifyNoInteractions(courseRepository);
    }

    @Test
    void find_active_by_ids_keys_the_result_by_the_input_string_not_the_canonical_id() {
        CourseId id = CourseId.generate();
        String upperCaseInput = id.toString().toUpperCase();
        when(courseRepository.findActiveByIds(any())).thenReturn(List.of(activeCourse(id)));

        Map<String, PhysicalCourseSummary> result = port.findActiveByIds(List.of(upperCaseInput));

        assertThat(result).containsOnlyKeys(upperCaseInput);
        assertThat(result.get(upperCaseInput).courseId()).isEqualTo(id.toString());
    }

    @Test
    void find_active_by_ids_looks_a_duplicated_id_up_once_and_answers_every_input_form() {
        CourseId id = CourseId.generate();
        String lowerCaseInput = id.toString();
        String upperCaseInput = id.toString().toUpperCase();
        when(courseRepository.findActiveByIds(any())).thenReturn(List.of(activeCourse(id)));

        Map<String, PhysicalCourseSummary> result = port.findActiveByIds(
            List.of(lowerCaseInput, lowerCaseInput, upperCaseInput)
        );

        assertThat(queriedIds()).containsExactly(id);
        assertThat(result).containsOnlyKeys(lowerCaseInput, upperCaseInput);
    }

    @Test
    void find_active_by_ids_omits_the_ids_the_repository_did_not_answer() {
        CourseId active = CourseId.generate();
        CourseId notVisible = CourseId.generate();
        when(courseRepository.findActiveByIds(any())).thenReturn(List.of(activeCourse(active)));

        Map<String, PhysicalCourseSummary> result =
            port.findActiveByIds(List.of(active.toString(), notVisible.toString()));

        assertThat(queriedIds()).containsExactlyInAnyOrder(active, notVisible);
        assertThat(result).containsOnlyKeys(active.toString());
    }

    @Test
    void find_active_by_ids_rejects_a_null_collection() {
        assertThatThrownBy(() -> port.findActiveByIds(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void find_active_by_ids_maps_a_hit_to_the_same_summary_as_find_active_by_id() {
        CourseId id = CourseId.generate();
        when(courseRepository.findActiveById(id)).thenReturn(Optional.of(activeCourse(id)));
        when(courseRepository.findActiveByIds(any())).thenReturn(List.of(activeCourse(id)));

        PhysicalCourseSummary single = port.findActiveById(id.toString()).orElseThrow();
        PhysicalCourseSummary batch =
            port.findActiveByIds(List.of(id.toString())).get(id.toString());

        assertThat(batch).isEqualTo(single);
    }

    private static PhysicalCourse activeCourse(CourseId id) {
        return new PhysicalCourse(
            id, "Salsa inicial", "desc", UUID.randomUUID(), "María García", DayOfWeek.TUESDAY,
            LocalTime.of(19, 0), 60, PhysicalCourseLevel.BEGINNER, 20, CourseStatus.ACTIVE
        );
    }

    @SuppressWarnings("unchecked")
    private Collection<CourseId> queriedIds() {
        ArgumentCaptor<Collection<CourseId>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(courseRepository).findActiveByIds(captor.capture());
        return captor.getValue();
    }
}
