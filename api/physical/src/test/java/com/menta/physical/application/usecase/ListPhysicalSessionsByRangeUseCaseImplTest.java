package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.PhysicalRangeSessionView;
import com.menta.physical.application.dto.SessionWithCourse;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.exception.InvalidSessionRangeException;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.domain.model.SessionStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListPhysicalSessionsByRangeUseCaseImplTest {

    // A Buenos Aires day (UTC-3), already converted to UTC by the client: [03:00Z, 03:00Z of the next day).
    private static final Instant DAY_FROM = Instant.parse("2026-10-10T03:00:00Z");
    private static final Instant DAY_TO = Instant.parse("2026-10-11T03:00:00Z");

    private final PhysicalSessionRepository sessionRepository = mock(PhysicalSessionRepository.class);
    private final ListPhysicalSessionsByRangeUseCaseImpl useCase =
        new ListPhysicalSessionsByRangeUseCaseImpl(sessionRepository);

    private static SessionWithCourse row(
        String scheduledAt, String courseTitle, int durationMinutes, int capacity, int assigned, int holds,
        SessionStatus status
    ) {
        PhysicalSession session = new PhysicalSession(
            SessionId.generate(), CourseId.generate(), Instant.parse(scheduledAt), capacity, assigned, holds,
            status, null
        );
        return new SessionWithCourse(session, courseTitle, durationMinutes);
    }

    @Test
    void queries_the_repository_with_the_given_instants_untouched() {
        when(sessionRepository.findManagedAcrossCourses(DAY_FROM, DAY_TO)).thenReturn(List.of());

        List<PhysicalRangeSessionView> views = useCase.list(DAY_FROM, DAY_TO);

        assertThat(views).isEmpty();
        verify(sessionRepository).findManagedAcrossCourses(DAY_FROM, DAY_TO);
    }

    @Test
    void a_range_of_exactly_seven_days_is_accepted() {
        Instant to = DAY_FROM.plus(Duration.ofDays(7));
        when(sessionRepository.findManagedAcrossCourses(DAY_FROM, to)).thenReturn(List.of());

        assertThat(useCase.list(DAY_FROM, to)).isEmpty();

        verify(sessionRepository).findManagedAcrossCourses(DAY_FROM, to);
    }

    @Test
    void a_range_one_nanosecond_over_seven_days_is_rejected_without_reading() {
        Instant to = DAY_FROM.plus(Duration.ofDays(7)).plusNanos(1);

        assertThatThrownBy(() -> useCase.list(DAY_FROM, to)).isInstanceOf(InvalidSessionRangeException.class);

        verifyNoInteractions(sessionRepository);
    }

    @Test
    void an_empty_range_is_rejected_without_reading() {
        assertThatThrownBy(() -> useCase.list(DAY_FROM, DAY_FROM)).isInstanceOf(InvalidSessionRangeException.class);

        verifyNoInteractions(sessionRepository);
    }

    @Test
    void an_inverted_range_is_rejected_without_reading() {
        assertThatThrownBy(() -> useCase.list(DAY_TO, DAY_FROM)).isInstanceOf(InvalidSessionRangeException.class);

        verifyNoInteractions(sessionRepository);
    }

    @Test
    void maps_every_row_keeping_the_repository_order() {
        SessionWithCourse first = row("2026-10-10T13:00:00Z", "Bachata", 60, 20, 4, 0, SessionStatus.SCHEDULED);
        SessionWithCourse second = row("2026-10-10T22:00:00Z", "Salsa", 90, 15, 10, 2, SessionStatus.SCHEDULED);
        when(sessionRepository.findManagedAcrossCourses(DAY_FROM, DAY_TO)).thenReturn(List.of(first, second));

        List<PhysicalRangeSessionView> views = useCase.list(DAY_FROM, DAY_TO);

        assertThat(views).extracting(PhysicalRangeSessionView::courseName).containsExactly("Bachata", "Salsa");
        assertThat(views.get(0)).isEqualTo(new PhysicalRangeSessionView(
            first.session().getId().getValue(), first.session().getCourseId().getValue(), "Bachata",
            Instant.parse("2026-10-10T13:00:00Z"), Instant.parse("2026-10-10T14:00:00Z"),
            SessionStatus.SCHEDULED, 20, 4
        ));
    }

    @Test
    void the_end_is_the_start_plus_the_course_duration() {
        SessionWithCourse ninetyMinutes = row("2026-10-10T22:00:00Z", "Salsa", 90, 15, 0, 0, SessionStatus.SCHEDULED);
        when(sessionRepository.findManagedAcrossCourses(DAY_FROM, DAY_TO)).thenReturn(List.of(ninetyMinutes));

        PhysicalRangeSessionView view = useCase.list(DAY_FROM, DAY_TO).get(0);

        assertThat(view.endsAt()).isEqualTo(Instant.parse("2026-10-10T23:30:00Z"));
    }

    @Test
    void occupied_counts_assignments_plus_active_holds_like_the_availability_arithmetic() {
        SessionWithCourse held = row("2026-10-10T22:00:00Z", "Salsa", 60, 20, 5, 3, SessionStatus.SCHEDULED);
        when(sessionRepository.findManagedAcrossCourses(DAY_FROM, DAY_TO)).thenReturn(List.of(held));

        PhysicalRangeSessionView view = useCase.list(DAY_FROM, DAY_TO).get(0);

        assertThat(view.capacity()).isEqualTo(20);
        assertThat(view.occupied()).isEqualTo(8);
        assertThat(held.session().getAvailableSpots()).isEqualTo(view.capacity() - view.occupied());
    }

    @Test
    void a_cancelled_session_is_listed_with_its_status() {
        SessionWithCourse cancelled = row("2026-10-10T22:00:00Z", "Salsa", 60, 20, 6, 0, SessionStatus.CANCELLED);
        when(sessionRepository.findManagedAcrossCourses(DAY_FROM, DAY_TO)).thenReturn(List.of(cancelled));

        PhysicalRangeSessionView view = useCase.list(DAY_FROM, DAY_TO).get(0);

        assertThat(view.status()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(view.occupied()).isEqualTo(6);
    }
}
