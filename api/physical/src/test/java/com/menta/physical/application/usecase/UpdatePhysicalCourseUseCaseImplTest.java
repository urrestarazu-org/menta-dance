package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.PhysicalCourseManagementResult;
import com.menta.physical.application.dto.UpdatePhysicalCourseCommand;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.exception.CourseHasActiveAssignmentsException;
import com.menta.physical.domain.exception.CourseNotFoundException;
import com.menta.physical.domain.exception.CourseNotOwnedException;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdatePhysicalCourseUseCaseImplTest {

    private final PhysicalCourseRepository courseRepository = mock(PhysicalCourseRepository.class);
    private final PhysicalSessionRepository sessionRepository = mock(PhysicalSessionRepository.class);
    private final UpdatePhysicalCourseUseCaseImpl useCase =
        new UpdatePhysicalCourseUseCaseImpl(courseRepository, sessionRepository);

    private static final UpdatePhysicalCourseCommand EMPTY_PATCH = new UpdatePhysicalCourseCommand(
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
        Optional.empty(), Optional.empty(), Optional.empty()
    );

    private static PhysicalCourse course(CourseId id, UUID professorId, CourseStatus status) {
        return new PhysicalCourse(
            id, "Salsa inicial", "desc", professorId, "María García", DayOfWeek.WEDNESDAY,
            LocalTime.of(20, 0), 60, PhysicalCourseLevel.INTERMEDIATE, 20, status
        );
    }

    @Test
    void throws_when_the_course_does_not_exist() {
        CourseId id = CourseId.generate();
        when(courseRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.update(id.toString(), EMPTY_PATCH, UUID.randomUUID(), true))
            .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void instructor_editing_a_course_they_do_not_own_is_rejected() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        UUID otherInstructor = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId, CourseStatus.ACTIVE)));

        assertThatThrownBy(() -> useCase.update(id.toString(), EMPTY_PATCH, otherInstructor, false))
            .isInstanceOf(CourseNotOwnedException.class);
        verify(courseRepository, never()).save(any());
    }

    @Test
    void instructor_editing_their_own_course_is_allowed() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId, CourseStatus.ACTIVE)));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePhysicalCourseCommand patch = new UpdatePhysicalCourseCommand(
            Optional.of("Salsa avanzada"), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );

        PhysicalCourseManagementResult result = useCase.update(id.toString(), patch, ownerId, false);

        assertThat(result.title()).isEqualTo("Salsa avanzada");
    }

    @Test
    void admin_bypasses_the_ownership_check() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId, CourseStatus.ACTIVE)));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PhysicalCourseManagementResult result =
            useCase.update(id.toString(), EMPTY_PATCH, UUID.randomUUID(), true);

        assertThat(result).isNotNull();
    }

    @Test
    void partial_patch_only_changes_the_fields_present() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId, CourseStatus.ACTIVE)));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePhysicalCourseCommand patch = new UpdatePhysicalCourseCommand(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.of(30), Optional.empty()
        );

        PhysicalCourseManagementResult result = useCase.update(id.toString(), patch, ownerId, false);

        assertThat(result.capacity()).isEqualTo(30);
        assertThat(result.title()).isEqualTo("Salsa inicial");
    }

    @Test
    void deactivating_a_course_without_future_assigned_sessions_succeeds() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId, CourseStatus.ACTIVE)));
        when(sessionRepository.hasFutureAssignedSessions(id)).thenReturn(false);
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePhysicalCourseCommand patch = new UpdatePhysicalCourseCommand(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.of(CourseStatus.INACTIVE)
        );

        PhysicalCourseManagementResult result = useCase.update(id.toString(), patch, ownerId, false);

        assertThat(result.status()).isEqualTo("INACTIVE");
    }

    @Test
    void deactivating_a_course_with_future_assigned_sessions_is_rejected() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId, CourseStatus.ACTIVE)));
        when(sessionRepository.hasFutureAssignedSessions(id)).thenReturn(true);

        UpdatePhysicalCourseCommand patch = new UpdatePhysicalCourseCommand(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.of(CourseStatus.INACTIVE)
        );

        assertThatThrownBy(() -> useCase.update(id.toString(), patch, ownerId, false))
            .isInstanceOf(CourseHasActiveAssignmentsException.class);
        verify(courseRepository, never()).save(any());
    }

    @Test
    void updating_a_field_other_than_status_never_triggers_the_deactivation_guard() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId, CourseStatus.ACTIVE)));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePhysicalCourseCommand patch = new UpdatePhysicalCourseCommand(
            Optional.of("Salsa avanzada"), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );

        useCase.update(id.toString(), patch, ownerId, false);

        verify(sessionRepository, never()).hasFutureAssignedSessions(any());
    }
}
