package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.exception.CourseNotOwnedException;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionStatus;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ListManagedPhysicalSessionsUseCaseImplTest {

    private final PhysicalCourseRepository courseRepository = mock(PhysicalCourseRepository.class);
    private final PhysicalSessionRepository sessionRepository = mock(PhysicalSessionRepository.class);
    private final ListManagedPhysicalSessionsUseCaseImpl useCase =
        new ListManagedPhysicalSessionsUseCaseImpl(courseRepository, sessionRepository);

    private static PhysicalCourse course(CourseId id, UUID professorId) {
        return new PhysicalCourse(
            id, "Salsa inicial", "desc", professorId, "María García", DayOfWeek.WEDNESDAY,
            LocalTime.of(20, 0), 60, PhysicalCourseLevel.INTERMEDIATE, 20, CourseStatus.ACTIVE
        );
    }

    @Test
    void instructor_listing_sessions_of_a_course_they_do_not_own_is_rejected() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId)));
        Instant from = Instant.now();
        Instant to = from.plusSeconds(86400);

        assertThatThrownBy(() -> useCase.list(id.toString(), from, to, UUID.randomUUID(), false))
            .isInstanceOf(CourseNotOwnedException.class);
    }

    @Test
    void returns_sessions_of_every_status_including_cancelled() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId)));
        Instant from = Instant.now();
        Instant to = from.plusSeconds(86400);
        PhysicalSession cancelled = new PhysicalSession(
            com.menta.physical.domain.model.SessionId.generate(), id, from.plusSeconds(3600),
            20, 0, 0, SessionStatus.CANCELLED, null
        );
        when(sessionRepository.findManaged(id, from, to)).thenReturn(List.of(cancelled));

        List<PhysicalSessionManagementResult> results = useCase.list(id.toString(), from, to, ownerId, false);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo("CANCELLED");
    }

    @Test
    void resolves_a_wide_default_window_when_from_and_to_are_omitted() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId)));
        when(sessionRepository.findManaged(eq(id), notNull(), notNull())).thenReturn(List.of());

        useCase.list(id.toString(), null, null, ownerId, false);

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(sessionRepository).findManaged(eq(id), fromCaptor.capture(), toCaptor.capture());
        Duration window = Duration.between(fromCaptor.getValue(), toCaptor.getValue());
        assertThat(window).isCloseTo(Duration.ofDays(7300), Duration.ofSeconds(5));
    }
}
