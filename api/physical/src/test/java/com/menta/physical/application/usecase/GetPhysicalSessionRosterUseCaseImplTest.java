package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.menta.physical.application.dto.PhysicalSessionRosterView;
import com.menta.physical.application.dto.RosterAttendanceStatus;
import com.menta.physical.application.dto.SessionRosterRow;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.exception.CourseNotFoundException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.domain.model.AttendanceKind;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.domain.model.SessionStatus;
import com.menta.shared.auth.UserDisplayNamePort;
import com.menta.shared.physical.AssignmentType;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

class GetPhysicalSessionRosterUseCaseImplTest {

    private static final Instant SCHEDULED_AT = Instant.parse("2026-10-10T22:00:00Z");
    private static final Instant CHECKED_IN_AT = Instant.parse("2026-10-10T21:47:12Z");

    private final PhysicalSessionRepository sessionRepository = mock(PhysicalSessionRepository.class);
    private final PhysicalCourseRepository courseRepository = mock(PhysicalCourseRepository.class);
    private final PhysicalCapacityAssignmentRepository assignmentRepository =
        mock(PhysicalCapacityAssignmentRepository.class);
    private final UserDisplayNamePort displayNames = mock(UserDisplayNamePort.class);
    private final GetPhysicalSessionRosterUseCaseImpl useCase = new GetPhysicalSessionRosterUseCaseImpl(
        sessionRepository, courseRepository, assignmentRepository, displayNames
    );

    private Logger logger;
    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(GetPhysicalSessionRosterUseCaseImpl.class);
        events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(events);
    }

    private PhysicalSession givenSession(SessionStatus status, int capacity) {
        PhysicalCourse course = new PhysicalCourse(
            CourseId.generate(), "Salsa Intermedio", "desc", UUID.randomUUID(), "Ana Perez", DayOfWeek.SATURDAY,
            LocalTime.of(22, 0), 60, PhysicalCourseLevel.INTERMEDIATE, 20, CourseStatus.ACTIVE
        );
        PhysicalSession session = new PhysicalSession(
            SessionId.generate(), course.getId(), SCHEDULED_AT, capacity, 0, 0, status, null
        );
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        return session;
    }

    private PhysicalSession givenSession() {
        return givenSession(SessionStatus.SCHEDULED, 20);
    }

    private static SessionRosterRow pending(UUID studentId, AssignmentType type) {
        return new SessionRosterRow(studentId, type, null, null);
    }

    private void givenRoster(PhysicalSession session, SessionRosterRow... rows) {
        when(assignmentRepository.findRoster(session.getId())).thenReturn(List.of(rows));
    }

    private void givenNames(Map<UUID, String> names) {
        when(displayNames.displayNamesOf(any())).thenReturn(names);
    }

    @Test
    void carries_the_session_header_with_the_course_name_and_the_roster_size() {
        PhysicalSession session = givenSession(SessionStatus.SCHEDULED, 18);
        UUID ana = UUID.randomUUID();
        UUID bruno = UUID.randomUUID();
        givenRoster(session, pending(ana, AssignmentType.MONTHLY), pending(bruno, AssignmentType.INDIVIDUAL));
        givenNames(Map.of(ana, "Ana Perez", bruno, "Bruno Diaz"));

        PhysicalSessionRosterView view = useCase.get(session.getId().toString());

        assertThat(view.session()).isEqualTo(new PhysicalSessionRosterView.SessionHeader(
            session.getId().getValue(), session.getCourseId().getValue(), "Salsa Intermedio", SCHEDULED_AT,
            SessionStatus.SCHEDULED, 18, 2
        ));
    }

    @Test
    void a_cancelled_session_still_has_its_roster_and_reports_its_status() {
        PhysicalSession session = givenSession(SessionStatus.CANCELLED, 20);
        UUID ana = UUID.randomUUID();
        givenRoster(session, pending(ana, AssignmentType.MONTHLY));
        givenNames(Map.of(ana, "Ana Perez"));

        PhysicalSessionRosterView view = useCase.get(session.getId().toString());

        assertThat(view.session().status()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(view.students()).hasSize(1);
    }

    @Test
    void a_student_with_a_check_in_is_present_with_its_time_and_variant_and_the_others_are_pending() {
        PhysicalSession session = givenSession();
        UUID qr = UUID.randomUUID();
        UUID manual = UUID.randomUUID();
        UUID absent = UUID.randomUUID();
        givenRoster(
            session,
            new SessionRosterRow(qr, AssignmentType.MONTHLY, CHECKED_IN_AT, AttendanceKind.QR),
            new SessionRosterRow(
                manual, AssignmentType.INDIVIDUAL, CHECKED_IN_AT.plusSeconds(60), AttendanceKind.MANUAL
            ),
            pending(absent, AssignmentType.MONTHLY)
        );
        givenNames(Map.of(qr, "Ana Perez", manual, "Bruno Diaz", absent, "Carla Gomez"));

        List<PhysicalSessionRosterView.Student> students = useCase.get(session.getId().toString()).students();

        assertThat(students).containsExactly(
            new PhysicalSessionRosterView.Student(
                qr, "Ana Perez", AssignmentType.MONTHLY, RosterAttendanceStatus.PRESENT, CHECKED_IN_AT,
                AttendanceKind.QR
            ),
            new PhysicalSessionRosterView.Student(
                manual, "Bruno Diaz", AssignmentType.INDIVIDUAL, RosterAttendanceStatus.PRESENT,
                CHECKED_IN_AT.plusSeconds(60), AttendanceKind.MANUAL
            ),
            new PhysicalSessionRosterView.Student(
                absent, "Carla Gomez", AssignmentType.MONTHLY, RosterAttendanceStatus.PENDING, null, null
            )
        );
    }

    @Test
    void students_are_ordered_by_display_name_ignoring_case_and_accents_and_then_by_id() {
        PhysicalSession session = givenSession();
        UUID zoe = UUID.randomUUID();
        UUID angela = UUID.randomUUID();
        UUID beto = UUID.randomUUID();
        UUID firstAna = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID secondAna = UUID.fromString("00000000-0000-4000-8000-000000000002");
        givenRoster(
            session, pending(zoe, AssignmentType.MONTHLY), pending(secondAna, AssignmentType.MONTHLY),
            pending(beto, AssignmentType.MONTHLY), pending(angela, AssignmentType.MONTHLY),
            pending(firstAna, AssignmentType.MONTHLY)
        );
        givenNames(Map.of(
            zoe, "Zoe Alvarez", angela, "Ángela Diaz", beto, "beto Diaz", firstAna, "Ana Perez",
            secondAna, "Ana Perez"
        ));

        List<PhysicalSessionRosterView.Student> students = useCase.get(session.getId().toString()).students();

        assertThat(students).extracting(PhysicalSessionRosterView.Student::studentId)
            .containsExactly(firstAna, secondAna, angela, beto, zoe);
    }

    @Test
    void every_name_is_resolved_in_one_single_batch_for_the_whole_roster() {
        PhysicalSession session = givenSession();
        UUID ana = UUID.randomUUID();
        UUID bruno = UUID.randomUUID();
        UUID carla = UUID.randomUUID();
        givenRoster(
            session, pending(ana, AssignmentType.MONTHLY), pending(bruno, AssignmentType.MONTHLY),
            pending(carla, AssignmentType.INDIVIDUAL)
        );
        givenNames(Map.of(ana, "Ana Perez", bruno, "Bruno Diaz", carla, "Carla Gomez"));

        useCase.get(session.getId().toString());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> requested = ArgumentCaptor.forClass(Collection.class);
        verify(displayNames, times(1)).displayNamesOf(requested.capture());
        assertThat(requested.getValue()).containsExactlyInAnyOrder(ana, bruno, carla);
    }

    @Test
    void a_student_unknown_to_auth_is_shown_by_a_neutral_text_and_only_the_count_is_logged() {
        PhysicalSession session = givenSession();
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.fromString("8a1f3c52-0000-4000-8000-000000000001");
        UUID otherUnknown = UUID.fromString("0b2e4d63-0000-4000-8000-000000000002");
        givenRoster(
            session, pending(known, AssignmentType.MONTHLY), pending(unknown, AssignmentType.MONTHLY),
            pending(otherUnknown, AssignmentType.INDIVIDUAL)
        );
        givenNames(Map.of(known, "Ana Perez"));

        PhysicalSessionRosterView view = useCase.get(session.getId().toString());

        assertThat(view.students()).extracting(PhysicalSessionRosterView.Student::displayName)
            .containsExactly("Alumno 0b2e4d63", "Alumno 8a1f3c52", "Ana Perez");
        assertThat(events.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("count=2").contains(session.getId().toString());
            assertThat(event.getFormattedMessage())
                .doesNotContain("8a1f3c52", "0b2e4d63", known.toString(), "Ana");
        });
    }

    @Test
    void nothing_is_logged_when_every_student_is_known() {
        PhysicalSession session = givenSession();
        UUID ana = UUID.randomUUID();
        givenRoster(session, pending(ana, AssignmentType.MONTHLY));
        givenNames(Map.of(ana, "Ana Perez"));

        useCase.get(session.getId().toString());

        assertThat(events.list).isEmpty();
    }

    @Test
    void an_empty_roster_skips_the_name_lookup() {
        PhysicalSession session = givenSession();
        givenRoster(session);

        PhysicalSessionRosterView view = useCase.get(session.getId().toString());

        assertThat(view.students()).isEmpty();
        assertThat(view.session().studentCount()).isZero();
        verifyNoInteractions(displayNames);
    }

    @Test
    void an_unknown_session_is_not_found_and_nothing_else_is_read() {
        SessionId unknown = SessionId.generate();
        when(sessionRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(unknown.toString())).isInstanceOf(SessionNotFoundException.class);

        verifyNoInteractions(courseRepository, assignmentRepository, displayNames);
    }

    @Test
    void a_session_whose_course_is_missing_is_a_course_not_found_error() {
        PhysicalSession session = givenSession();
        when(courseRepository.findById(session.getCourseId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(session.getId().toString()))
            .isInstanceOf(CourseNotFoundException.class);

        verify(assignmentRepository, never()).findRoster(any());
    }

    @Test
    void a_malformed_session_id_is_rejected_before_reading_anything() {
        assertThatThrownBy(() -> useCase.get("no-es-un-uuid")).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(sessionRepository, courseRepository, assignmentRepository, displayNames);
    }
}
