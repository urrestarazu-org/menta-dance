package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.CreatePhysicalSessionCommand;
import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.exception.CourseNotFoundException;
import com.menta.physical.domain.exception.CourseNotOwnedException;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreatePhysicalSessionUseCaseImplTest {

    private final PhysicalCourseRepository courseRepository = mock(PhysicalCourseRepository.class);
    private final PhysicalSessionRepository sessionRepository = mock(PhysicalSessionRepository.class);
    private final CreatePhysicalSessionUseCaseImpl useCase =
        new CreatePhysicalSessionUseCaseImpl(courseRepository, sessionRepository);

    private static PhysicalCourse course(CourseId id, UUID professorId) {
        return new PhysicalCourse(
            id, "Salsa inicial", "desc", professorId, "María García", DayOfWeek.WEDNESDAY,
            LocalTime.of(20, 0), 60, PhysicalCourseLevel.INTERMEDIATE, 20, CourseStatus.ACTIVE
        );
    }

    @Test
    void throws_when_the_course_does_not_exist() {
        CourseId id = CourseId.generate();
        when(courseRepository.findById(id)).thenReturn(Optional.empty());

        CreatePhysicalSessionCommand command =
            new CreatePhysicalSessionCommand(LocalDate.of(2026, 9, 15), LocalTime.of(19, 0), null, null);

        assertThatThrownBy(() -> useCase.create(id.toString(), command, UUID.randomUUID(), true))
            .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void instructor_creating_a_session_for_a_course_they_do_not_own_is_rejected() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId)));

        CreatePhysicalSessionCommand command =
            new CreatePhysicalSessionCommand(LocalDate.of(2026, 9, 15), LocalTime.of(19, 0), null, null);

        assertThatThrownBy(() -> useCase.create(id.toString(), command, UUID.randomUUID(), false))
            .isInstanceOf(CourseNotOwnedException.class);
    }

    @Test
    void capacity_is_inherited_from_the_course_when_not_specified() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId)));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreatePhysicalSessionCommand command =
            new CreatePhysicalSessionCommand(LocalDate.of(2026, 9, 15), LocalTime.of(19, 0), null, null);

        PhysicalSessionManagementResult result = useCase.create(id.toString(), command, ownerId, false);

        assertThat(result.capacity()).isEqualTo(20);
    }

    @Test
    void an_explicit_capacity_overrides_the_course_default() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId)));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreatePhysicalSessionCommand command =
            new CreatePhysicalSessionCommand(LocalDate.of(2026, 9, 15), LocalTime.of(19, 0), 10, "Clase especial");

        PhysicalSessionManagementResult result = useCase.create(id.toString(), command, ownerId, false);

        assertThat(result.capacity()).isEqualTo(10);
        assertThat(result.notes()).isEqualTo("Clase especial");
    }

    @Test
    void admin_creates_a_session_for_a_course_they_do_not_own() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId)));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreatePhysicalSessionCommand command =
            new CreatePhysicalSessionCommand(LocalDate.of(2026, 9, 15), LocalTime.of(19, 0), null, null);

        PhysicalSessionManagementResult result = useCase.create(id.toString(), command, UUID.randomUUID(), true);

        assertThat(result.courseId()).isEqualTo(id.toString());
    }
}
