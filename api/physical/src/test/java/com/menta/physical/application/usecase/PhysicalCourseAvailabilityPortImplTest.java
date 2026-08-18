package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
            id, "Salsa inicial", "María García", DayOfWeek.TUESDAY,
            LocalTime.of(19, 0), PhysicalCourseLevel.BEGINNER, 20, CourseStatus.ACTIVE
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
            id, "Salsa inicial", "María García", DayOfWeek.TUESDAY,
            LocalTime.of(19, 0), PhysicalCourseLevel.BEGINNER, 20, CourseStatus.ACTIVE
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
        PhysicalSession session = new PhysicalSession(sessionId, courseId, scheduledAt, 20, 5, 3);
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
}
