package com.menta.physical.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.application.port.in.PhysicalCapacityAssignmentPort;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.domain.model.SessionStatus;
import com.menta.physical.infrastructure.qr.QrProperties;
import com.menta.shared.demo.DemoIds;
import com.menta.shared.demo.DemoSeedOrder;
import com.menta.shared.physical.CapacityAssignmentCommand;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DemoPhysicalCatalogFixtureTest {

    /** A Wednesday; the first regular Sensual slot is Thursday 2026-10-08 19:00 UTC. */
    private static final Instant NOW = Instant.parse("2026-10-07T17:00:00Z");
    private static final Instant NEXT_SENSUAL = Instant.parse("2026-10-08T19:00:00Z");
    private static final CourseId SENSUAL = CourseId.of(DemoIds.PHYSICAL_SENSUAL_COURSE_ID);
    private static final CourseId TRADITIONAL = CourseId.of(DemoIds.PHYSICAL_TRADITIONAL_COURSE_ID);
    private static final SessionId ROLLING = SessionId.of(DemoPhysicalCatalogFixture.ROLLING_SESSION_ID);

    private final PhysicalCourseRepository courses = mock(PhysicalCourseRepository.class);
    private final PhysicalSessionRepository sessions = mock(PhysicalSessionRepository.class);
    private final PhysicalCapacityAssignmentRepository assignments =
        mock(PhysicalCapacityAssignmentRepository.class);
    private final PhysicalCapacityAssignmentPort assignmentPort = mock(PhysicalCapacityAssignmentPort.class);
    private final Clock clock = () -> NOW;
    private final DemoPhysicalCatalogFixture fixture = new DemoPhysicalCatalogFixture(
        courses, sessions, assignments, assignmentPort, clock, new QrProperties());

    @Test
    void absent_courses_are_saved_with_the_instructor_as_professor() {
        fixture.run(mock());

        ArgumentCaptor<PhysicalCourse> captor = ArgumentCaptor.forClass(PhysicalCourse.class);
        verify(courses, times(2)).save(captor.capture());
        PhysicalCourse sensual = captor.getAllValues().get(0);
        PhysicalCourse traditional = captor.getAllValues().get(1);
        assertThat(sensual.getId()).isEqualTo(SENSUAL);
        assertThat(sensual.getTitle()).isEqualTo("Bachata Sensual Presencial");
        assertThat(sensual.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(sensual.getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(sensual.getCapacity()).isEqualTo(12);
        assertThat(traditional.getId()).isEqualTo(TRADITIONAL);
        assertThat(traditional.getTitle()).isEqualTo("Bachata Tradicional Presencial");
        assertThat(traditional.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(traditional.getStartTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(traditional.getCapacity()).isEqualTo(15);
        assertThat(captor.getAllValues()).allSatisfy(course -> {
            assertThat(course.getProfessorId()).isEqualTo(DemoIds.INSTRUCTOR_USER_ID);
            assertThat(course.getStatus()).isEqualTo(CourseStatus.ACTIVE);
        });
    }

    @Test
    void present_courses_are_not_saved_again() {
        when(courses.findById(any())).thenAnswer(call -> Optional.of(mock(PhysicalCourse.class)));

        fixture.run(mock());

        verify(courses, never()).save(any());
    }

    @Test
    void empty_database_creates_eight_weeks_of_slots_in_utc() {
        fixture.run(mock());

        ArgumentCaptor<List<PhysicalSession>> captor = ArgumentCaptor.forClass(List.class);
        verify(sessions, times(2)).saveAll(captor.capture());
        List<PhysicalSession> sensual = captor.getAllValues().get(0);
        List<PhysicalSession> traditional = captor.getAllValues().get(1);
        assertThat(sensual).extracting(PhysicalSession::getScheduledAt).containsExactly(
            Instant.parse("2026-10-08T19:00:00Z"), Instant.parse("2026-10-13T19:00:00Z"),
            Instant.parse("2026-10-15T19:00:00Z"), Instant.parse("2026-10-20T19:00:00Z"),
            Instant.parse("2026-10-22T19:00:00Z"), Instant.parse("2026-10-27T19:00:00Z"),
            Instant.parse("2026-10-29T19:00:00Z"), Instant.parse("2026-11-03T19:00:00Z"),
            Instant.parse("2026-11-05T19:00:00Z"), Instant.parse("2026-11-10T19:00:00Z"),
            Instant.parse("2026-11-12T19:00:00Z"), Instant.parse("2026-11-17T19:00:00Z"),
            Instant.parse("2026-11-19T19:00:00Z"), Instant.parse("2026-11-24T19:00:00Z"),
            Instant.parse("2026-11-26T19:00:00Z"), Instant.parse("2026-12-01T19:00:00Z"));
        assertThat(traditional).extracting(PhysicalSession::getScheduledAt).containsExactly(
            Instant.parse("2026-10-10T11:00:00Z"), Instant.parse("2026-10-17T11:00:00Z"),
            Instant.parse("2026-10-24T11:00:00Z"), Instant.parse("2026-10-31T11:00:00Z"),
            Instant.parse("2026-11-07T11:00:00Z"), Instant.parse("2026-11-14T11:00:00Z"),
            Instant.parse("2026-11-21T11:00:00Z"), Instant.parse("2026-11-28T11:00:00Z"));
        assertThat(sensual).allSatisfy(session -> {
            assertThat(session.getCourseId()).isEqualTo(SENSUAL);
            assertThat(session.getCapacity()).isEqualTo(12);
            assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
        });
        assertThat(traditional).allSatisfy(session -> assertThat(session.getCapacity()).isEqualTo(15));
    }

    @Test
    void a_slot_earlier_today_is_not_created() {
        Instant tuesdayNight = Instant.parse("2026-10-13T20:00:00Z");
        new DemoPhysicalCatalogFixture(
            courses, sessions, assignments, assignmentPort, () -> tuesdayNight, new QrProperties()).run(mock());

        ArgumentCaptor<List<PhysicalSession>> captor = ArgumentCaptor.forClass(List.class);
        verify(sessions, times(2)).saveAll(captor.capture());
        assertThat(captor.getAllValues().get(0)).extracting(PhysicalSession::getScheduledAt)
            .first().isEqualTo(Instant.parse("2026-10-15T19:00:00Z"));
    }

    @Test
    void partially_seeded_database_only_receives_the_missing_slots() {
        when(sessions.findManaged(any(), any(), any())).thenAnswer(call ->
            SENSUAL.equals(call.getArgument(0))
                ? List.of(PhysicalSession.create(SENSUAL, NEXT_SENSUAL, 12, null))
                : List.of());

        fixture.run(mock());

        ArgumentCaptor<List<PhysicalSession>> captor = ArgumentCaptor.forClass(List.class);
        verify(sessions, times(2)).saveAll(captor.capture());
        assertThat(captor.getAllValues().get(0)).hasSize(15)
            .extracting(PhysicalSession::getScheduledAt).doesNotContain(NEXT_SENSUAL);
    }

    @Test
    void fully_seeded_database_saves_no_session_batch() {
        when(sessions.findManaged(any(), any(), any())).thenAnswer(call -> {
            CourseId course = call.getArgument(0);
            return slots(course);
        });

        fixture.run(mock());

        verify(sessions, never()).saveAll(any());
    }

    @Test
    void absent_rolling_session_is_created_ten_minutes_ahead() {
        fixture.run(mock());

        ArgumentCaptor<PhysicalSession> captor = ArgumentCaptor.forClass(PhysicalSession.class);
        verify(sessions).save(captor.capture());
        PhysicalSession rolling = captor.getValue();
        assertThat(rolling.getId()).isEqualTo(ROLLING);
        assertThat(rolling.getCourseId()).isEqualTo(SENSUAL);
        assertThat(rolling.getScheduledAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(rolling.getCapacity()).isEqualTo(12);
        assertThat(rolling.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
    }

    @Test
    void rolling_session_inside_the_check_in_window_is_reused() {
        givenRollingAt(NOW.plus(Duration.ofMinutes(10)));

        fixture.run(mock());

        verify(sessions, never()).save(any());
    }

    @Test
    void rolling_session_still_reachable_at_both_window_edges_is_reused() {
        givenRollingAt(NOW.plus(Duration.ofMinutes(30)));
        fixture.run(mock());
        givenRollingAt(NOW.minus(Duration.ofMinutes(90)));
        fixture.run(mock());

        verify(sessions, never()).save(any());
    }

    @Test
    void rolling_session_that_is_too_far_ahead_or_too_old_is_moved_with_the_same_id() {
        givenRollingAt(NOW.plus(Duration.ofMinutes(30)).plusSeconds(1));
        fixture.run(mock());
        givenRollingAt(NOW.minus(Duration.ofMinutes(90)).minusSeconds(1));
        fixture.run(mock());

        ArgumentCaptor<PhysicalSession> captor = ArgumentCaptor.forClass(PhysicalSession.class);
        verify(sessions, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(moved -> {
            assertThat(moved.getId()).isEqualTo(ROLLING);
            assertThat(moved.getScheduledAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        });
    }

    @Test
    void rolling_session_is_never_counted_as_a_regular_slot_or_regular_seat() {
        PhysicalSession rolling = new PhysicalSession(
            ROLLING, SENSUAL, NOW.plus(Duration.ofMinutes(10)), 12, 0, 0, SessionStatus.SCHEDULED, null);
        when(sessions.findById(ROLLING)).thenReturn(Optional.of(rolling));
        when(sessions.findManaged(any(), any(), any())).thenAnswer(call ->
            SENSUAL.equals(call.getArgument(0)) ? List.of(rolling) : List.of());

        fixture.run(mock());

        ArgumentCaptor<List<PhysicalSession>> captor = ArgumentCaptor.forClass(List.class);
        verify(sessions, times(2)).saveAll(captor.capture());
        assertThat(captor.getAllValues().get(0)).hasSize(16);
        ArgumentCaptor<CapacityAssignmentCommand> seats = ArgumentCaptor.forClass(CapacityAssignmentCommand.class);
        verify(assignmentPort, times(2)).assign(seats.capture());
        assertThat(seats.getAllValues()).extracting(CapacityAssignmentCommand::sessionId)
            .containsOnlyOnce(ROLLING.getValue());
    }

    @Test
    void missing_seats_are_assigned_in_the_rolling_and_the_next_regular_sensual_session() {
        fixture.run(mock());

        ArgumentCaptor<List<PhysicalSession>> batch = ArgumentCaptor.forClass(List.class);
        verify(sessions, times(2)).saveAll(batch.capture());
        PhysicalSession nextRegular = batch.getAllValues().get(0).get(0);
        ArgumentCaptor<CapacityAssignmentCommand> seats = ArgumentCaptor.forClass(CapacityAssignmentCommand.class);
        verify(assignmentPort, times(2)).assign(seats.capture());
        assertThat(seats.getAllValues()).containsExactly(
            new CapacityAssignmentCommand(
                ROLLING.getValue(), DemoIds.STUDENT_USER_ID, DemoPhysicalCatalogFixture.SEAT_PAYMENT_ID),
            new CapacityAssignmentCommand(
                nextRegular.getId().getValue(), DemoIds.STUDENT_USER_ID,
                DemoPhysicalCatalogFixture.SEAT_PAYMENT_ID));
        assertThat(nextRegular.getScheduledAt()).isEqualTo(NEXT_SENSUAL);
    }

    @Test
    void existing_confirmed_seats_are_not_assigned_again() {
        when(assignments.existsConfirmedAssignment(any(), any())).thenReturn(true);

        fixture.run(mock());

        verify(assignmentPort, never()).assign(any());
    }

    @Test
    void runs_at_the_content_order() {
        assertThat(fixture.getOrder()).isEqualTo(DemoSeedOrder.CONTENT);
    }

    private void givenRollingAt(Instant scheduledAt) {
        when(sessions.findById(ROLLING)).thenReturn(Optional.of(
            new PhysicalSession(ROLLING, SENSUAL, scheduledAt, 12, 0, 0, SessionStatus.SCHEDULED, null)));
    }

    private List<PhysicalSession> slots(CourseId course) {
        ArgumentCaptor<List<PhysicalSession>> captor = ArgumentCaptor.forClass(List.class);
        PhysicalSessionRepository probe = mock(PhysicalSessionRepository.class);
        new DemoPhysicalCatalogFixture(courses, probe, assignments, assignmentPort, clock, new QrProperties())
            .run(mock());
        verify(probe, times(2)).saveAll(captor.capture());
        return SENSUAL.equals(course) ? captor.getAllValues().get(0) : captor.getAllValues().get(1);
    }
}
