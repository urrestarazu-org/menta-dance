package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.PhysicalDaySessionView;
import com.menta.physical.application.dto.SessionWithCourse;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.domain.model.SessionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListPhysicalSessionsByDateUseCaseImplTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 10);
    // The academy day 2026-10-10 is [03:00Z, 03:00Z of the next day) in Buenos Aires (UTC-3).
    private static final Instant DAY_FROM = Instant.parse("2026-10-10T03:00:00Z");
    private static final Instant DAY_TO = Instant.parse("2026-10-11T03:00:00Z");

    private final PhysicalSessionRepository sessionRepository = mock(PhysicalSessionRepository.class);

    private ListPhysicalSessionsByDateUseCaseImpl useCase(ZoneId zone) {
        return new ListPhysicalSessionsByDateUseCaseImpl(sessionRepository, zone);
    }

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
    void queries_the_half_open_range_of_the_day_in_the_academy_zone() {
        when(sessionRepository.findManagedAcrossCourses(DAY_FROM, DAY_TO)).thenReturn(List.of());

        List<PhysicalDaySessionView> views = useCase(BUENOS_AIRES).list(DAY);

        assertThat(views).isEmpty();
        verify(sessionRepository).findManagedAcrossCourses(DAY_FROM, DAY_TO);
    }

    @Test
    void the_day_follows_the_configured_zone_not_utc() {
        ZoneId utc = ZoneId.of("UTC");
        when(sessionRepository.findManagedAcrossCourses(
            Instant.parse("2026-10-10T00:00:00Z"), Instant.parse("2026-10-11T00:00:00Z")
        )).thenReturn(List.of());

        useCase(utc).list(DAY);

        verify(sessionRepository).findManagedAcrossCourses(
            Instant.parse("2026-10-10T00:00:00Z"), Instant.parse("2026-10-11T00:00:00Z")
        );
    }

    @Test
    void a_day_with_a_daylight_saving_change_is_not_assumed_to_last_24_hours() {
        ZoneId newYork = ZoneId.of("America/New_York");
        // 2026-03-08: clocks jump forward at 02:00, so the local day lasts 23 hours.
        Instant from = Instant.parse("2026-03-08T05:00:00Z");
        Instant to = Instant.parse("2026-03-09T04:00:00Z");
        when(sessionRepository.findManagedAcrossCourses(from, to)).thenReturn(List.of());

        useCase(newYork).list(LocalDate.of(2026, 3, 8));

        verify(sessionRepository).findManagedAcrossCourses(from, to);
    }

    @Test
    void maps_every_row_keeping_the_repository_order() {
        SessionWithCourse first = row("2026-10-10T13:00:00Z", "Bachata", 60, 20, 4, 0, SessionStatus.SCHEDULED);
        SessionWithCourse second = row("2026-10-10T22:00:00Z", "Salsa", 90, 15, 10, 2, SessionStatus.SCHEDULED);
        when(sessionRepository.findManagedAcrossCourses(DAY_FROM, DAY_TO)).thenReturn(List.of(first, second));

        List<PhysicalDaySessionView> views = useCase(BUENOS_AIRES).list(DAY);

        assertThat(views).extracting(PhysicalDaySessionView::courseName).containsExactly("Bachata", "Salsa");
        assertThat(views.get(0)).isEqualTo(new PhysicalDaySessionView(
            first.session().getId().getValue(), first.session().getCourseId().getValue(), "Bachata",
            Instant.parse("2026-10-10T13:00:00Z"), Instant.parse("2026-10-10T14:00:00Z"),
            SessionStatus.SCHEDULED, 20, 4
        ));
    }

    @Test
    void the_end_is_the_start_plus_the_course_duration() {
        SessionWithCourse ninetyMinutes = row("2026-10-10T22:00:00Z", "Salsa", 90, 15, 0, 0, SessionStatus.SCHEDULED);
        when(sessionRepository.findManagedAcrossCourses(DAY_FROM, DAY_TO)).thenReturn(List.of(ninetyMinutes));

        PhysicalDaySessionView view = useCase(BUENOS_AIRES).list(DAY).get(0);

        assertThat(view.endsAt()).isEqualTo(Instant.parse("2026-10-10T23:30:00Z"));
    }

    @Test
    void occupied_counts_assignments_plus_active_holds_like_the_availability_arithmetic() {
        SessionWithCourse held = row("2026-10-10T22:00:00Z", "Salsa", 60, 20, 5, 3, SessionStatus.SCHEDULED);
        when(sessionRepository.findManagedAcrossCourses(DAY_FROM, DAY_TO)).thenReturn(List.of(held));

        PhysicalDaySessionView view = useCase(BUENOS_AIRES).list(DAY).get(0);

        assertThat(view.capacity()).isEqualTo(20);
        assertThat(view.occupied()).isEqualTo(8);
        assertThat(held.session().getAvailableSpots()).isEqualTo(view.capacity() - view.occupied());
    }

    @Test
    void a_cancelled_session_is_listed_with_its_status() {
        SessionWithCourse cancelled = row("2026-10-10T22:00:00Z", "Salsa", 60, 20, 6, 0, SessionStatus.CANCELLED);
        when(sessionRepository.findManagedAcrossCourses(DAY_FROM, DAY_TO)).thenReturn(List.of(cancelled));

        PhysicalDaySessionView view = useCase(BUENOS_AIRES).list(DAY).get(0);

        assertThat(view.status()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(view.occupied()).isEqualTo(6);
    }
}
