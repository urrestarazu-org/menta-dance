package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.BatchCreatePhysicalSessionsCommand;
import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.exception.CourseNotOwnedException;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchCreatePhysicalSessionsUseCaseImplTest {

    private final PhysicalCourseRepository courseRepository = mock(PhysicalCourseRepository.class);
    private final PhysicalSessionRepository sessionRepository = mock(PhysicalSessionRepository.class);
    private final BatchCreatePhysicalSessionsUseCaseImpl useCase =
        new BatchCreatePhysicalSessionsUseCaseImpl(courseRepository, sessionRepository);

    private static PhysicalCourse course(CourseId id, UUID professorId) {
        // A course recurring every WEDNESDAY.
        return new PhysicalCourse(
            id, "Salsa inicial", "desc", professorId, "María García", DayOfWeek.WEDNESDAY,
            LocalTime.of(20, 0), 60, PhysicalCourseLevel.INTERMEDIATE, 20, CourseStatus.ACTIVE
        );
    }

    @Test
    void instructor_batch_creating_for_a_course_they_do_not_own_is_rejected() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId)));

        BatchCreatePhysicalSessionsCommand command =
            new BatchCreatePhysicalSessionsCommand(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThatThrownBy(() -> useCase.createBatch(id.toString(), command, UUID.randomUUID(), false))
            .isInstanceOf(CourseNotOwnedException.class);
    }

    @Test
    void generates_one_session_per_matching_day_of_week_in_the_range() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId)));
        when(sessionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // September 2026 has Wednesdays on the 2nd, 9th, 16th, 23rd and 30th.
        BatchCreatePhysicalSessionsCommand command =
            new BatchCreatePhysicalSessionsCommand(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        List<PhysicalSessionManagementResult> results = useCase.createBatch(id.toString(), command, ownerId, false);

        assertThat(results).hasSize(5);
        assertThat(results).allMatch(result -> result.capacity() == 20);
        assertThat(results).allMatch(result -> result.scheduledAt().startsWith("2026-09"));
    }

    @Test
    void a_range_with_no_matching_day_of_week_generates_no_sessions() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId)));
        when(sessionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BatchCreatePhysicalSessionsCommand command =
            new BatchCreatePhysicalSessionsCommand(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 8));

        List<PhysicalSessionManagementResult> results = useCase.createBatch(id.toString(), command, ownerId, false);

        assertThat(results).isEmpty();
    }
}
