package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import com.menta.physical.application.dto.UpdatePhysicalSessionCommand;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import com.menta.physical.domain.exception.CourseNotOwnedException;
import com.menta.physical.domain.exception.SessionAlreadyOccurredException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.domain.model.SessionStatus;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdatePhysicalSessionUseCaseImplTest {

    private final PhysicalCourseRepository courseRepository = mock(PhysicalCourseRepository.class);
    private final PhysicalSessionRepository sessionRepository = mock(PhysicalSessionRepository.class);
    private final UpdatePhysicalSessionUseCaseImpl useCase =
        new UpdatePhysicalSessionUseCaseImpl(courseRepository, sessionRepository);

    private static final UpdatePhysicalSessionCommand EMPTY_PATCH = new UpdatePhysicalSessionCommand(
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
    );

    private static PhysicalCourse course(CourseId id, UUID professorId) {
        return new PhysicalCourse(
            id, "Salsa inicial", "desc", professorId, "María García", DayOfWeek.WEDNESDAY,
            LocalTime.of(20, 0), 60, PhysicalCourseLevel.INTERMEDIATE, 20, CourseStatus.ACTIVE
        );
    }

    private static PhysicalSession session(
        SessionId id, CourseId courseId, Instant scheduledAt, int capacity, int assignedSpots
    ) {
        return new PhysicalSession(
            id, courseId, scheduledAt, capacity, assignedSpots, 0, SessionStatus.SCHEDULED, null
        );
    }

    @Test
    void throws_when_the_session_does_not_exist() {
        SessionId id = SessionId.generate();
        when(sessionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.update(id.toString(), EMPTY_PATCH, UUID.randomUUID(), true))
            .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void instructor_editing_a_session_of_a_course_they_do_not_own_is_rejected() {
        SessionId sessionId = SessionId.generate();
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        Instant future = Instant.now().plusSeconds(86400);
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.of(session(sessionId, courseId, future, 20, 0)));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));

        assertThatThrownBy(() -> useCase.update(sessionId.toString(), EMPTY_PATCH, UUID.randomUUID(), false))
            .isInstanceOf(CourseNotOwnedException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void modifying_a_session_without_assigned_spots_succeeds() {
        SessionId sessionId = SessionId.generate();
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        Instant future = Instant.now().plusSeconds(86400);
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.of(session(sessionId, courseId, future, 20, 0)));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePhysicalSessionCommand patch = new UpdatePhysicalSessionCommand(
            Optional.empty(), Optional.empty(), Optional.of(15), Optional.empty(), Optional.empty()
        );

        PhysicalSessionManagementResult result = useCase.update(sessionId.toString(), patch, ownerId, false);

        assertThat(result.capacity()).isEqualTo(15);
    }

    @Test
    void reducing_capacity_below_assigned_spots_is_rejected() {
        SessionId sessionId = SessionId.generate();
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        Instant future = Instant.now().plusSeconds(86400);
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.of(session(sessionId, courseId, future, 20, 10)));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));

        UpdatePhysicalSessionCommand patch = new UpdatePhysicalSessionCommand(
            Optional.empty(), Optional.empty(), Optional.of(5), Optional.empty(), Optional.empty()
        );

        assertThatThrownBy(() -> useCase.update(sessionId.toString(), patch, ownerId, false))
            .isInstanceOf(CapacityBelowAssignedException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void cancelling_a_future_session_succeeds() {
        SessionId sessionId = SessionId.generate();
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        Instant future = Instant.now().plusSeconds(86400);
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.of(session(sessionId, courseId, future, 20, 5)));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePhysicalSessionCommand patch = new UpdatePhysicalSessionCommand(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(SessionStatus.CANCELLED)
        );

        PhysicalSessionManagementResult result = useCase.update(sessionId.toString(), patch, ownerId, false);

        assertThat(result.status()).isEqualTo("CANCELLED");
    }

    @Test
    void modifying_a_session_that_already_occurred_is_rejected() {
        SessionId sessionId = SessionId.generate();
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        Instant past = Instant.now().minusSeconds(86400);
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.of(session(sessionId, courseId, past, 20, 0)));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));

        assertThatThrownBy(() -> useCase.update(sessionId.toString(), EMPTY_PATCH, ownerId, false))
            .isInstanceOf(SessionAlreadyOccurredException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void cancelling_a_session_that_already_occurred_is_also_rejected() {
        SessionId sessionId = SessionId.generate();
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        Instant past = Instant.now().minusSeconds(86400);
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.of(session(sessionId, courseId, past, 20, 5)));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));

        UpdatePhysicalSessionCommand patch = new UpdatePhysicalSessionCommand(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(SessionStatus.CANCELLED)
        );

        assertThatThrownBy(() -> useCase.update(sessionId.toString(), patch, ownerId, false))
            .isInstanceOf(SessionAlreadyOccurredException.class);
    }

    @Test
    void rescheduling_changes_date_and_time_together() {
        SessionId sessionId = SessionId.generate();
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        Instant future = Instant.parse("2026-09-15T22:00:00Z");
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.of(session(sessionId, courseId, future, 20, 0)));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePhysicalSessionCommand patch = new UpdatePhysicalSessionCommand(
            Optional.of(java.time.LocalDate.of(2026, 9, 20)), Optional.of(java.time.LocalTime.of(18, 0)),
            Optional.empty(), Optional.empty(), Optional.empty()
        );

        PhysicalSessionManagementResult result = useCase.update(sessionId.toString(), patch, ownerId, false);

        assertThat(result.scheduledAt()).isEqualTo("2026-09-20T18:00:00Z");
    }

    @Test
    void rescheduling_only_the_time_keeps_the_original_date() {
        SessionId sessionId = SessionId.generate();
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        Instant future = Instant.parse("2026-09-15T22:00:00Z");
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.of(session(sessionId, courseId, future, 20, 0)));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePhysicalSessionCommand patch = new UpdatePhysicalSessionCommand(
            Optional.empty(), Optional.of(java.time.LocalTime.of(18, 0)),
            Optional.empty(), Optional.empty(), Optional.empty()
        );

        PhysicalSessionManagementResult result = useCase.update(sessionId.toString(), patch, ownerId, false);

        assertThat(result.scheduledAt()).isEqualTo("2026-09-15T18:00:00Z");
    }

    @Test
    void updating_notes_changes_only_notes() {
        SessionId sessionId = SessionId.generate();
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        Instant future = Instant.now().plusSeconds(86400);
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.of(session(sessionId, courseId, future, 20, 0)));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePhysicalSessionCommand patch = new UpdatePhysicalSessionCommand(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("Clase de repaso"), Optional.empty()
        );

        PhysicalSessionManagementResult result = useCase.update(sessionId.toString(), patch, ownerId, false);

        assertThat(result.notes()).isEqualTo("Clase de repaso");
        assertThat(result.capacity()).isEqualTo(20);
    }

    @Test
    void admin_bypasses_the_ownership_check() {
        SessionId sessionId = SessionId.generate();
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        Instant future = Instant.now().plusSeconds(86400);
        when(sessionRepository.findById(sessionId))
            .thenReturn(Optional.of(session(sessionId, courseId, future, 20, 0)));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PhysicalSessionManagementResult result =
            useCase.update(sessionId.toString(), EMPTY_PATCH, UUID.randomUUID(), true);

        assertThat(result).isNotNull();
    }
}
